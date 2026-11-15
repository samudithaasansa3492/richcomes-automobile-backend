package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.employee.entity.Employee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityModelTest {
    @Test
    void independentDeveloperHasOnlyExplicitTechnicalAuthority() {
        var admin = new Role("ADMIN", "Developer");
        admin.grantPermission(new Permission("employee:read", "Accidental business grant"));
        var account = UserAccount.technical("developer.one", "encoded-hash", admin);
        account.enable();
        assertNull(account.getEmployee());
        assertTrue(account.isTechnical());
        assertTrue(com.richcomes.garage.auth.security.AccountAccessPolicy.canAuthenticate(account));
        assertEquals(java.util.Set.of("technical:recovery"),
                com.richcomes.garage.auth.security.AccountAccessPolicy.effectivePermissions(account));
        account.assignRole(new Role("OWNER", "Forbidden mixed role"));
        assertFalse(com.richcomes.garage.auth.security.AccountAccessPolicy.canAuthenticate(account));
        assertTrue(com.richcomes.garage.auth.security.AccountAccessPolicy.effectivePermissions(account).isEmpty());
    }

    @Test
    void recoveredPasswordIncrementsTokenVersion() {
        var account = UserAccount.technical("developer.one", "old-hash", new Role("ADMIN", "Developer"));
        account.resetPasswordHash("new-hash");
        assertEquals(1, account.getCredentialVersion());
        assertEquals("new-hash", account.getPasswordHash());
        assertThrows(IllegalArgumentException.class, () -> account.resetPasswordHash(" "));
        assertEquals(1, account.getCredentialVersion());
    }

    @Test
    void rejectsInvalidUsernames() {
        assertThrows(IllegalArgumentException.class, () -> UserAccount.normalizeUsername(null));
        for (String input : new String[]{"", "ab", "cash ier", "-cashier", "a".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> UserAccount.normalizeUsername(input));
        }
    }

    @Test
    void rejectsBlankPasswordHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserAccount(new Employee(), "cashier", " "));
    }

    @Test
    void accessCollectionsCanOnlyBeChangedThroughExplicitMethods() {
        Role role = new Role("CASHIER", "Cashier");
        Permission read = new Permission("employee:read", "Read employees");
        role.grantPermission(read);
        role.grantPermission(new Permission("employee:read", "Same permission code"));
        assertEquals(1, role.getPermissions().size());
        assertThrows(UnsupportedOperationException.class, () -> role.getPermissions().clear());
        role.revokePermission(read);
        assertTrue(role.getPermissions().isEmpty());
    }
}
