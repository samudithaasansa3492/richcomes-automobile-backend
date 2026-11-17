package com.richcomes.garage.auth.recovery;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real service/JPA transactions, private H2 only; delivery is captured without network or secret logging. */
@SpringJUnitConfig(RecoveryPersistenceTest.Config.class)
class RecoveryPersistenceTest {
    private static final String PASSWORD = "initial synthetic password";
    private static final String NEW_PASSWORD = "replacement synthetic password";
    @Autowired RecoveryService service;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired RecoveryChallengeRepository challenges;
    @Autowired SecurityAuditRepository audit;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Autowired TestDelivery delivery;
    @Autowired TestClock clock;
    private Long accountId;
    private TransactionTemplate tx;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        delivery.enabled = true; delivery.fail = false; delivery.token = null; delivery.email = null;
        clock.now.set(Instant.parse("2026-11-15T00:00:00Z"));
        tx.executeWithoutResult(status -> {
            challenges.deleteAll(); audit.deleteAll(); accounts.deleteAll(); roles.deleteAll();
            // Hibernate queues deletes after inserts. Flush cleanup before reusing unique role codes.
            roles.flush();
            var role = roles.saveAndFlush(new Role("ADMIN", "Developer"));
            var account = UserAccount.technical("developer.one", encoder.encode(PASSWORD), role);
            account.enable(); accountId = accounts.saveAndFlush(account).getId();
        });
    }
    private String enroll() {
        service.enroll(accountId, PASSWORD, "verified@example.invalid");
        return delivery.token;
    }
    private void verified() { service.verifyEmail(enroll()); clock.advance(61); }
    private UserAccount account() { return accounts.findOneById(accountId).orElseThrow(); }

    @Test void emailIsUnverifiedUntilRecipientConfirmsAndOnlyHashIsStored() {
        String token = enroll();
        assertNull(account().getRecoveryEmail());
        assertEquals(43, token.length());
        assertEquals("verified@example.invalid", delivery.email);
        assertEquals(RecoveryService.hash(token), challenges.findAll().getFirst().tokenHash);
        service.verifyEmail(token);
        assertEquals("verified@example.invalid", account().getRecoveryEmail());
        assertEquals(0, challenges.count());
        assertThrows(RecoveryFailure.class, () -> service.verifyEmail(token));
    }

    @Test void enrollmentRequiresCurrentPasswordAndDelivery() {
        assertThrows(RecoveryFailure.class, () -> service.enroll(accountId, "wrong password", "other@example.invalid"));
        assertNull(delivery.token);
        delivery.enabled = false;
        assertThrows(RecoveryFailure.class, this::enroll);
        assertEquals(0, challenges.count());
    }

    @Test void recoveryUsesVerifiedDestinationAndRevokesExistingTokensWithoutEnablingOrChangingRoles() {
        verified();
        service.request("developer.one", 99L);
        String token = delivery.token;
        assertEquals("verified@example.invalid", delivery.email);
        service.reset(token, NEW_PASSWORD);
        var saved = account();
        assertTrue(encoder.matches(NEW_PASSWORD, saved.getPasswordHash()));
        assertFalse(encoder.matches(PASSWORD, saved.getPasswordHash()));
        assertEquals(1, saved.getCredentialVersion());
        assertTrue(saved.isEnabled()); assertNull(saved.getEmployee());
        assertEquals("ADMIN", saved.getRoles().iterator().next().getCode());
        assertEquals(0, challenges.count());
        assertTrue(audit.findAll().stream().anyMatch(e -> "RESET_PASSWORD_REQUESTED".equals(e.eventCode) && Long.valueOf(99).equals(e.actorId)));
        assertThrows(RecoveryFailure.class, () -> service.reset(token, "another synthetic password"));
    }

    @Test void unknownAndUnverifiedRequestsDoNotSendMail() {
        service.request("absent.user", null);
        service.request("developer.one", null);
        assertNull(delivery.token); assertEquals(0, challenges.count());
    }

    @Test void deliveryIsThrottledAcrossRequests() {
        verified(); service.request("developer.one", null);
        String token = delivery.token;
        service.request("developer.one", 99L);
        assertEquals(token, delivery.token);
        assertEquals(1, challenges.count());
    }

    @Test void newerRequestInvalidatesEarlierLink() {
        verified(); service.request("developer.one", null); String old = delivery.token;
        clock.advance(61); service.request("developer.one", null);
        assertNotEquals(old, delivery.token);
        assertThrows(RecoveryFailure.class, () -> service.reset(old, NEW_PASSWORD));
        service.reset(delivery.token, NEW_PASSWORD);
    }

    @Test void expiryIsExclusiveAndPurposeCannotBeSwapped() {
        String verification = enroll();
        assertThrows(RecoveryFailure.class, () -> service.reset(verification, NEW_PASSWORD));
        clock.advance(900);
        assertThrows(RecoveryFailure.class, () -> service.verifyEmail(verification));
        assertNull(account().getRecoveryEmail());
    }

    @Test void changedCredentialsInvalidateOutstandingChallenge() {
        verified(); service.request("developer.one", null); String token = delivery.token;
        tx.executeWithoutResult(status -> accounts.findById(accountId).orElseThrow().revokeTokens());
        assertThrows(RecoveryFailure.class, () -> service.reset(token, NEW_PASSWORD));
    }

    @Test void disabledAccountCannotBeReenabledThroughRecovery() {
        verified(); service.request("developer.one", null); String token = delivery.token;
        tx.executeWithoutResult(status -> accounts.findById(accountId).orElseThrow().disable());
        assertThrows(RecoveryFailure.class, () -> service.reset(token, NEW_PASSWORD));
        assertFalse(account().isEnabled());
    }

    @Test void smtpFailureRollsBackChallengeThrottleAndAudit() {
        verified(); long auditCount = audit.count();
        var lastSent = account().getRecoveryLastSentAt();
        delivery.fail = true;
        assertThrows(RecoveryFailure.class, () -> service.request("developer.one", 99L));
        assertEquals(0, challenges.count()); assertEquals(auditCount, audit.count());
        assertEquals(lastSent, account().getRecoveryLastSentAt());
    }

    @Test void rejectsMalformedAndOverByteLimitPasswordWithoutConsumingChallenge() {
        verified(); service.request("developer.one", null); String token = delivery.token;
        assertThrows(RecoveryFailure.class, () -> service.reset("bad", NEW_PASSWORD));
        assertThrows(RecoveryFailure.class, () -> service.reset(token, "é".repeat(37)));
        assertThrows(RecoveryFailure.class, () -> service.reset(token, "short"));
        service.reset(token, NEW_PASSWORD);
    }

    @Test void concurrentRedemptionCommitsExactlyOnce() throws Exception {
        verified(); service.request("developer.one", null); String token = delivery.token;
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> work = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { service.reset(token, NEW_PASSWORD); return true; }
                catch (RecoveryFailure expected) { return false; }
            };
            var first = pool.submit(work); var second = pool.submit(work);
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, account().getCredentialVersion());
            assertEquals(1, audit.findAll().stream().filter(e -> "PASSWORD_RECOVERED_TOKENS_REVOKED".equals(e.eventCode)).count());
        } finally { pool.shutdownNow(); }
    }

    static class TestDelivery implements RecoveryDelivery {
        boolean enabled = true, fail;
        volatile String token, email;
        public boolean available() { return enabled; }
        public void send(String email, String purpose, String token) {
            if (fail) throw new RecoveryFailure();
            this.email = email; this.token = token;
        }
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        void advance(long seconds) { now.updateAndGet(t -> t.plusSeconds(seconds)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserAccountRepository.class, RecoveryChallengeRepository.class})
    @Import(RecoveryService.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                    .addScript("classpath:auth/create-test-schema.sql").build();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setPackagesToScan("com.richcomes.garage.auth.entity", "com.richcomes.garage.employee.entity", "com.richcomes.garage.customer.entity",
                    "com.richcomes.garage.auth.recovery");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean TestClock authClock() { return new TestClock(); }
        @Bean TestDelivery delivery() { return new TestDelivery(); }
    }
}
