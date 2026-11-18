package com.richcomes.garage.auth.recovery;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountAccessPolicy;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerInitialPasswordTest {
    @Mock UserAccountRepository accounts;
    @Mock RecoveryChallengeRepository challenges;
    @Mock SecurityAuditRepository audit;
    @Mock PasswordEncoder encoder;
    @Mock RecoveryDelivery delivery;
    @Mock EntityManager manager;

    private static final Instant NOW = Instant.parse("2026-11-18T08:00:00Z");
    private static final String TOKEN = "A".repeat(43);
    private static final String PASSWORD = "customer controls this secret";
    RecoveryService service;
    UserAccount customerAccount;
    Customer customer;

    @BeforeEach
    void init() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RecoveryService(accounts, challenges, audit, encoder, delivery, clock, manager);
        ReflectionTestUtils.setField(service, "onboardingEnabled", true);
        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 12L);
        customer.setEmail("customer@example.invalid");
        customer.setStatus(CustomerStatus.ACTIVE);
        customerAccount = UserAccount.customer(customer, "customer.one", "unknown-placeholder-hash",
                new Role("CUSTOMER", "Customer"));
        ReflectionTestUtils.setField(customerAccount, "id", 42L);
        customerAccount.recordCustomerIdentityProof(51L, "CASE-00123", customer.getEmail(), NOW);
    }

    @Test
    void customerInvitationAndPasswordSetupFailClosedWhenRolloutDisabled() {
        ReflectionTestUtils.setField(service, "onboardingEnabled", false);
        assertThrows(RecoveryFailure.class,
                () -> service.inviteCustomer(customerAccount, 51L));
        assertThrows(RecoveryFailure.class,
                () -> service.setupInitialPassword(TOKEN, PASSWORD));
        verifyNoInteractions(challenges, delivery, encoder, accounts, audit);
    }

    @Test
    void invitesVerifiedCustomerWithOneTimeTokenWithoutEnablingLogin() {
        when(delivery.available()).thenReturn(true);
        service.inviteCustomer(customerAccount, 51L);
        verify(delivery).send(eq(customer.getEmail()), eq("INITIAL_PASSWORD"),
                argThat(t -> t.matches("[A-Za-z0-9_-]{43}")));
        verify(challenges).deleteByAccountId(42L);
        verify(challenges).flush();
        verify(challenges).saveAndFlush(any(RecoveryChallenge.class));
        assertEquals(NOW, customerAccount.getRecoveryLastSentAt());
        assertFalse(customerAccount.isEnabled());
        assertFalse(AccountAccessPolicy.canAuthenticate(customerAccount));
    }

    @Test
    void initialPasswordIsSingleUseAndStillRequiresOwnerActivation() {
        var challenge = setupChallenge(NOW.plusSeconds(900), "INITIAL_PASSWORD");
        when(delivery.available()).thenReturn(true);
        when(challenges.findByTokenHash(RecoveryService.hash(TOKEN))).thenReturn(Optional.of(challenge));
        when(accounts.lockById(42L)).thenReturn(Optional.of(customerAccount));
        when(challenges.existsById(challenge.id)).thenReturn(true);
        when(challenges.findById(challenge.id)).thenReturn(Optional.of(challenge));
        when(encoder.encode(PASSWORD)).thenReturn("customer-bcrypt-hash");

        service.setupInitialPassword(TOKEN, PASSWORD);
        assertTrue(customerAccount.hasCustomerChosenPassword());
        assertEquals("customer-bcrypt-hash", customerAccount.getPasswordHash());
        assertFalse(customerAccount.isEnabled());
        assertFalse(AccountAccessPolicy.canAuthenticate(customerAccount));
        verify(challenges).deleteByAccountId(42L);
        assertThrows(RecoveryFailure.class, () -> service.setupInitialPassword(TOKEN, PASSWORD));
        verify(encoder, times(1)).encode(PASSWORD);
    }

    @Test
    void expiredInvitationCannotEstablishCredentials() {
        var challenge = setupChallenge(NOW, "INITIAL_PASSWORD");
        when(delivery.available()).thenReturn(true);
        when(challenges.findByTokenHash(RecoveryService.hash(TOKEN))).thenReturn(Optional.of(challenge));
        when(accounts.lockById(42L)).thenReturn(Optional.of(customerAccount));
        when(challenges.existsById(challenge.id)).thenReturn(true);
        when(challenges.findById(challenge.id)).thenReturn(Optional.of(challenge));

        assertThrows(RecoveryFailure.class, () -> service.setupInitialPassword(TOKEN, PASSWORD));
        assertFalse(customerAccount.hasCustomerChosenPassword());
        verifyNoInteractions(encoder);
    }

    @Test
    void normalPasswordResetTokenCannotBeUsedForCustomerSetup() {
        var challenge = setupChallenge(NOW.plusSeconds(900), "RESET_PASSWORD");
        when(delivery.available()).thenReturn(true);
        when(challenges.findByTokenHash(RecoveryService.hash(TOKEN))).thenReturn(Optional.of(challenge));
        when(accounts.lockById(42L)).thenReturn(Optional.of(customerAccount));
        when(challenges.existsById(challenge.id)).thenReturn(true);
        when(challenges.findById(challenge.id)).thenReturn(Optional.of(challenge));

        assertThrows(RecoveryFailure.class, () -> service.setupInitialPassword(TOKEN, PASSWORD));
        assertFalse(customerAccount.hasCustomerChosenPassword());
        verifyNoInteractions(encoder);
    }

    @Test
    void emailChangedAfterProofInvalidatesInvitation() {
        var challenge = setupChallenge(NOW.plusSeconds(900), "INITIAL_PASSWORD");
        customer.setEmail("different@example.invalid");
        when(delivery.available()).thenReturn(true);
        when(challenges.findByTokenHash(RecoveryService.hash(TOKEN))).thenReturn(Optional.of(challenge));
        when(accounts.lockById(42L)).thenReturn(Optional.of(customerAccount));
        when(challenges.existsById(challenge.id)).thenReturn(true);
        when(challenges.findById(challenge.id)).thenReturn(Optional.of(challenge));

        assertThrows(RecoveryFailure.class, () -> service.setupInitialPassword(TOKEN, PASSWORD));
        verifyNoInteractions(encoder);
    }

    private RecoveryChallenge setupChallenge(Instant expiry, String purpose) {
        return new RecoveryChallenge(42L, purpose, RecoveryService.hash(TOKEN),
                "customer@example.invalid", customerAccount.getCredentialVersion(), expiry);
    }
}
