package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.employee.entity.Employee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityModelTest {
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
