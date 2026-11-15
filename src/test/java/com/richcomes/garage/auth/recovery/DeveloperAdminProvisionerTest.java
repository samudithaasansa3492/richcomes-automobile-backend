package com.richcomes.garage.auth.recovery;

import com.richcomes.provisioning.DeveloperAdminProvisioner;
import com.richcomes.provisioning.ProvisioningException;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(RecoveryPersistenceTest.Config.class)
class DeveloperAdminProvisionerTest {
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder encoder;
    JdbcTemplate jdbc;
    DeveloperAdminProvisioner provisioner;
    static final String PASSWORD = "synthetic developer password";
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[]{"recovery_challenges", "security_audit_events", "user_account_roles",
                "role_permissions", "user_accounts", "roles", "permissions", "employees"}) {
            jdbc.execute("DELETE FROM garage." + table);
        }
        jdbc.update("INSERT INTO garage.roles (code, description) VALUES ('ADMIN', 'Developer')");
        provisioner = new DeveloperAdminProvisioner(dataSource::getConnection, encoder);
    }
    @Test void provisionsIndependentIdentityWithAdminOnlyAndAudit() {
        char[] secret = PASSWORD.toCharArray();
        long id = provisioner.provision(" Developer.One ", secret);
        assertArrayEquals(new char[secret.length], secret);
        assertEquals("developer.one", jdbc.queryForObject("SELECT username FROM garage.user_accounts WHERE id=?", String.class, id));
        assertEquals("TECHNICAL", jdbc.queryForObject("SELECT identity_kind FROM garage.user_accounts WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT employee_id FROM garage.user_accounts WHERE id=?", Object.class, id));
        assertEquals(0L, count("employees"));
        assertEquals(1L, count("user_account_roles"));
        assertEquals(1L, count("security_audit_events"));
        assertTrue(encoder.matches(PASSWORD, jdbc.queryForObject("SELECT password_hash FROM garage.user_accounts WHERE id=?", String.class, id)));
        assertNull(jdbc.queryForObject("SELECT recovery_email FROM garage.user_accounts WHERE id=?", String.class, id));
    }
    @Test void duplicateRefusesWithoutChangingExistingIdentity() {
        provisioner.provision("developer.one", PASSWORD.toCharArray());
        var exception = assertThrows(ProvisioningException.class,
                () -> provisioner.provision("DEVELOPER.ONE", PASSWORD.toCharArray()));
        assertEquals(ProvisioningException.Code.USERNAME_EXISTS, exception.getCode());
        assertEquals(1L, count("user_accounts")); assertEquals(1L, count("security_audit_events"));
    }
    @Test void missingRoleRefusesAndClearsPassword() {
        jdbc.update("DELETE FROM garage.roles");
        char[] secret = PASSWORD.toCharArray();
        var exception = assertThrows(ProvisioningException.class, () -> provisioner.provision("developer.one", secret));
        assertEquals(ProvisioningException.Code.SEED_REQUIRED, exception.getCode());
        assertArrayEquals(new char[secret.length], secret); assertEquals(0L, count("user_accounts"));
    }
    @Test void invalidPasswordsCannotCreateIdentities() {
        for (String input : new String[]{"short", " ".repeat(20), "é".repeat(37)}) {
            char[] secret = input.toCharArray();
            assertThrows(ProvisioningException.class, () -> provisioner.provision("developer.one", secret));
            assertArrayEquals(new char[secret.length], secret);
        }
        assertEquals(0L, count("user_accounts"));
    }
    @Test void auditFailureRollsBackAccountAndRoleAssignment() {
        var failing = new DeveloperAdminProvisioner(() -> {
            var real = dataSource.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0].toString().contains("security_audit_events")) {
                            throw new SQLException("synthetic failure");
                        }
                        try { return method.invoke(real, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        }, encoder);
        assertThrows(ProvisioningException.class, () -> failing.provision("developer.one", PASSWORD.toCharArray()));
        assertEquals(0L, count("user_accounts")); assertEquals(0L, count("user_account_roles"));
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM garage." + table, Long.class); }
}
