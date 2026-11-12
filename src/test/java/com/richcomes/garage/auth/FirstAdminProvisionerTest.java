package com.richcomes.garage.auth;

import com.richcomes.garage.auth.config.PasswordConfig;
import com.richcomes.provisioning.FirstAdminProvisioner;
import com.richcomes.provisioning.ProvisioningException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.richcomes.provisioning.ProvisioningException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

/** Private H2 mapping database only. PostgreSQL seed/locking require separate validation. */
@SpringJUnitConfig(IdentityPersistenceTest.TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FirstAdminProvisionerTest {
    private static final String PASSWORD = "synthetic admin password";
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc;
    private FirstAdminProvisioner provisioner;
    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
    private UUID employeeId;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[]{"user_account_roles", "role_permissions", "user_accounts",
                "roles", "permissions", "employees"}) jdbc.execute("DELETE FROM garage." + table);
        jdbc.update("INSERT INTO garage.roles(id, code, description) VALUES (1, 'OWNER_ADMIN', 'Owner')");
        String[] codes = {"employee:read", "employee:create", "employee:update", "employee:deactivate",
                "account:read", "account:create", "account:manage"};
        for (int i = 0; i < codes.length; i++) {
            jdbc.update("INSERT INTO garage.permissions(id, code, description) VALUES (?, ?, 'Test')", i + 1, codes[i]);
            jdbc.update("INSERT INTO garage.role_permissions(role_id, permission_id) VALUES (1, ?)", i + 1);
        }
        employeeId = employee();
        provisioner = new FirstAdminProvisioner(dataSource::getConnection, encoder);
    }

    @Test void createsEnabledOwnerWithNormalizedUsernameAndApplicationPasswordEncoder() {
        char[] password = PASSWORD.toCharArray();
        long id = provisioner.provision(employeeId, "  First.Admin  ", password);
        assertCleared(password);
        assertEquals("first.admin", jdbc.queryForObject("SELECT username FROM garage.user_accounts WHERE id=?", String.class, id));
        assertTrue(jdbc.queryForObject("SELECT enabled FROM garage.user_accounts WHERE id=?", Boolean.class, id));
        String hash = jdbc.queryForObject("SELECT password_hash FROM garage.user_accounts WHERE id=?", String.class, id);
        assertTrue(hash.startsWith("{bcrypt}"));
        assertTrue(encoder.matches(PASSWORD, hash));
        assertEquals(1L, jdbc.queryForObject("SELECT role_id FROM garage.user_account_roles WHERE user_account_id=?", Long.class, id));
        assertEquals(1, countAccounts());
    }

    @Test void refusesSecondActiveAdministratorWithoutChangingExistingAccount() {
        long id = provisioner.provision(employeeId, "first.admin", PASSWORD.toCharArray());
        String hash = jdbc.queryForObject("SELECT password_hash FROM garage.user_accounts WHERE id=?", String.class, id);
        assertCode(ADMIN_ALREADY_EXISTS, () -> provisioner.provision(employee(), "other.admin", PASSWORD.toCharArray()));
        assertEquals(1, countAccounts());
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM garage.user_accounts WHERE id=?", String.class, id));
    }

    @Test void rejectsMissingOrInactiveEmployee() {
        assertCode(EMPLOYEE_NOT_FOUND, () -> provisioner.provision(UUID.randomUUID(), "first.admin", PASSWORD.toCharArray()));
        jdbc.update("UPDATE garage.employees SET active=FALSE WHERE id=?", employeeId);
        assertCode(EMPLOYEE_INACTIVE, () -> provisioner.provision(employeeId, "first.admin", PASSWORD.toCharArray()));
        assertEquals(0, countAccounts());
    }

    @Test void rejectsMissingRoleAndIncompletePermissions() {
        jdbc.update("DELETE FROM garage.role_permissions");
        assertCode(SEED_REQUIRED, () -> provisioner.provision(employeeId, "first.admin", PASSWORD.toCharArray()));
        jdbc.update("DELETE FROM garage.roles");
        assertCode(SEED_REQUIRED, () -> provisioner.provision(employeeId, "first.admin", PASSWORD.toCharArray()));
        assertEquals(0, countAccounts());
    }

    @Test void doesNotOverwriteDisabledAccountForEmployee() {
        long id = provisioner.provision(employeeId, "first.admin", PASSWORD.toCharArray());
        jdbc.update("UPDATE garage.user_accounts SET enabled=FALSE WHERE id=?", id);
        String hash = jdbc.queryForObject("SELECT password_hash FROM garage.user_accounts WHERE id=?", String.class, id);
        assertCode(EMPLOYEE_HAS_ACCOUNT, () -> provisioner.provision(employeeId, "different.name", PASSWORD.toCharArray()));
        assertFalse(jdbc.queryForObject("SELECT enabled FROM garage.user_accounts WHERE id=?", Boolean.class, id));
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM garage.user_accounts WHERE id=?", String.class, id));
    }

    @Test void doesNotOverwriteDuplicateNormalizedUsername() {
        long id = provisioner.provision(employeeId, "first.admin", PASSWORD.toCharArray());
        jdbc.update("UPDATE garage.user_accounts SET enabled=FALSE WHERE id=?", id);
        UUID other = employee();
        assertCode(USERNAME_EXISTS, () -> provisioner.provision(other, " FIRST.ADMIN ", PASSWORD.toCharArray()));
        assertEquals(1, countAccounts());
    }

    @Test void validatesPasswordBytesAndUsernameBeforeConnecting() {
        var disconnected = new FirstAdminProvisioner(() -> { fail("Must validate before connecting"); return null; }, encoder);
        for (String input : new String[]{"short", " ".repeat(20), "x".repeat(73), "ආ".repeat(25)}) {
            char[] password = input.toCharArray();
            assertCode(INVALID_INPUT, () -> disconnected.provision(employeeId, "first.admin", password));
            assertCleared(password);
        }
        char[] password = PASSWORD.toCharArray();
        assertCode(INVALID_INPUT, () -> disconnected.provision(employeeId, "bad username", password));
        assertCleared(password);
        assertCode(INVALID_INPUT, () -> disconnected.provision(null, "first.admin", PASSWORD.toCharArray()));
    }

    @Test void databaseErrorDoesNotExposeDriverDetailsAndClearsPassword() {
        var disconnected = new FirstAdminProvisioner(() -> { throw new SQLException("secret-value"); }, encoder);
        char[] password = PASSWORD.toCharArray();
        var failure = assertThrows(ProvisioningException.class,
                () -> disconnected.provision(employeeId, "first.admin", password));
        assertEquals(DATABASE_FAILURE, failure.getCode());
        assertFalse(failure.toString().contains("secret-value"));
        assertNull(failure.getCause());
        assertCleared(password);
    }

    @Test void roleAssignmentFailureRollsBackInsertedAccount() {
        var failing = new FirstAdminProvisioner(() -> {
            Connection real = dataSource.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.startsWith("INSERT INTO garage.user_account_roles")) {
                            throw new SQLException("synthetic assignment failure");
                        }
                        try { return method.invoke(real, args); }
                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                    });
        }, encoder);
        assertCode(DATABASE_FAILURE, () -> failing.provision(employeeId, "first.admin", PASSWORD.toCharArray()));
        assertEquals(0, countAccounts());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM garage.user_account_roles", Integer.class));
    }

    @Test void concurrentProvisioningCreatesExactlyOneAdministrator() throws Exception {
        UUID other = employee();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> attempt(employeeId, "first.admin", ready, start));
            var b = executor.submit(() -> attempt(other, "other.admin", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var results = Arrays.asList(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("CREATED"::equals).count());
            assertEquals(1, results.stream().filter("ADMIN_ALREADY_EXISTS"::equals).count());
            assertEquals(1, countAccounts());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private String attempt(UUID employee, String username, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { provisioner.provision(employee, username, PASSWORD.toCharArray()); return "CREATED"; }
        catch (ProvisioningException exception) { return exception.getCode().name(); }
    }

    private UUID employee() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 12);
        jdbc.update("""
                INSERT INTO garage.employees
                (id, employee_code, nic, first_name, last_name, phone, position, payment_rate, active)
                VALUES (?, ?, ?, 'Synthetic', 'Employee', '0700000000', 'OWNER_ADMIN', 0, TRUE)
                """, id, "EMP-" + suffix, suffix);
        return id;
    }

    private int countAccounts() { return jdbc.queryForObject("SELECT COUNT(*) FROM garage.user_accounts", Integer.class); }
    private void assertCleared(char[] chars) { assertArrayEquals(new char[chars.length], chars); }
    private void assertCode(ProvisioningException.Code expected, Runnable action) {
        assertEquals(expected, assertThrows(ProvisioningException.class, action::run).getCode());
    }
}
