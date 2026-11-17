package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.security.AccountAccessPolicy;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.entity.EmployeePosition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

class CustomerAccountAccessPolicyTest {
    @Test void customerCannotLoginUntilActivatedAndOnlyGetsSelfPermission() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 7L);
        customer.setStatus(CustomerStatus.ACTIVE);
        Role role = new Role("CUSTOMER", "Customer");
        role.grantPermission(new Permission("employee:read", "Unsafe accidental grant"));
        UserAccount account = UserAccount.customer(customer, "customer.one", "fake-encoded-hash", role);
        assertFalse(AccountAccessPolicy.canAuthenticate(account));
        account.enable();
        assertTrue(AccountAccessPolicy.canAuthenticate(account));
        assertEquals(Set.of("customer:self"), AccountAccessPolicy.effectivePermissions(account));
        AccountPrincipal principal = AccountPrincipal.from(account);
        assertEquals(7L, principal.customerId());
        assertNull(principal.employeeId());
        assertEquals(Set.of("CUSTOMER"), principal.roles());
        assertFalse(principal.permissions().contains("employee:read"));

        customer.setStatus(CustomerStatus.SUSPENDED);
        assertFalse(AccountAccessPolicy.canAuthenticate(account));
        assertTrue(AccountAccessPolicy.effectivePermissions(account).isEmpty());
    }

    @Test void customerWithStaffRoleIsRejectedEvenIfEnabled() {
        Customer customer = new Customer();
        customer.setStatus(CustomerStatus.ACTIVE);
        var account = UserAccount.customer(customer, "customer.one", "fake-encoded-hash",
                new Role("CUSTOMER", "Customer"));
        account.assignRole(new Role("OWNER", "Forbidden"));
        account.enable();
        assertFalse(AccountAccessPolicy.canAuthenticate(account));
        assertTrue(AccountAccessPolicy.effectivePermissions(account).isEmpty());
    }

    @Test void cashierDelegationAffectsExactlyOneAccountAndCanBeRevoked() {
        Employee employee1 = new Employee(); employee1.setPosition(EmployeePosition.CASHIER);
        Employee employee2 = new Employee(); employee2.setPosition(EmployeePosition.CASHIER);
        Role cashier = new Role("CASHIER", "Cashier");
        UserAccount chosen = new UserAccount(employee1, "cashier.one", "hash");
        chosen.assignRole(cashier); chosen.enable();
        UserAccount other = new UserAccount(employee2, "cashier.two", "hash");
        other.assignRole(cashier); other.enable();
        Permission register = new Permission("customer:register", "Register");
        // Even an erroneous role-wide grant must not authorize all CASHIER accounts.
        cashier.grantPermission(register);
        cashier.grantPermission(new Permission("customer:delegate_register", "OWNER-only action"));
        assertFalse(AccountAccessPolicy.effectivePermissions(chosen).contains("customer:register"));
        assertFalse(AccountAccessPolicy.effectivePermissions(other).contains("customer:register"));
        assertFalse(AccountAccessPolicy.effectivePermissions(other).contains("customer:delegate_register"));
        chosen.grantDirectPermission(register);

        assertTrue(AccountAccessPolicy.effectivePermissions(chosen).contains("customer:register"));
        assertFalse(AccountAccessPolicy.effectivePermissions(other).contains("customer:register"));
        chosen.revokeDirectPermission(register);
        assertFalse(AccountAccessPolicy.effectivePermissions(chosen).contains("customer:register"));
    }

    @Test void cashierCannotGainLegacyEmployeeAccountOrAuditAccessFromAnyGrantSource() {
        var staff = new Employee();
        staff.setPosition(EmployeePosition.CASHIER);
        var cashierRole = new Role("CASHIER", "Cashier");
        for (String code : Set.of("employee:read", "employee:create", "employee:update",
                "employee:deactivate", "account:read", "account:create", "account:manage",
                "audit:read", "permission:delegate")) {
            cashierRole.grantPermission(new Permission(code, "Unsafe shared role grant"));
        }
        var cashier = new UserAccount(staff, "cashier.legacy", "encoded-hash");
        cashier.assignRole(cashierRole);
        cashier.enable();
        cashier.grantDirectPermission(new Permission("account:manage", "Unsafe direct grant"));
        assertEquals(Set.of(), AccountAccessPolicy.effectivePermissions(cashier));
        // Only explicit allowlisted individual business access is effective.
        cashier.grantDirectPermission(new Permission("vehicle:read", "Read vehicles"));
        assertEquals(Set.of("vehicle:read"), AccountAccessPolicy.effectivePermissions(cashier));
    }

    @Test void previouslyPersistedHighImpactCashierGrantsRemainInert() {
        var staff = new Employee();
        staff.setPosition(EmployeePosition.CASHIER);
        var cashierRole = new Role("CASHIER", "Cashier");
        var cashier = new UserAccount(staff, "cashier.legacy", "encoded-hash");
        cashier.assignRole(cashierRole);
        cashier.enable();

        // Legacy direct grants are deliberately ineffective, even if already
        // persisted before the conservative authorization policy was adopted.
        for (String code : Set.of("customer:read", "customer:manage",
                "billing:read", "report:read", "report:export",
                "service:delete", "history:delete", "reminder:delete",
                "notification:delete")) {
            cashier.grantDirectPermission(new Permission(code, "Unapproved legacy grant"));
            cashierRole.grantPermission(new Permission(code, "Unsafe shared role grant"));
        }
        assertEquals(Set.of(), AccountAccessPolicy.effectivePermissions(cashier));

        cashier.grantDirectPermission(new Permission("vehicle:read", "Approved staff task"));
        assertEquals(Set.of("vehicle:read"), AccountAccessPolicy.effectivePermissions(cashier));
    }

    @Test void technicalAdminCannotReceiveBusinessRegistrationFromAccidentalGrant() {
        Role admin = new Role("ADMIN", "Technical");
        admin.grantPermission(new Permission("customer:register", "Unsafe grant"));
        var developer = UserAccount.technical("developer.one", "fake-encoded-hash", admin);
        developer.enable();
        assertEquals(Set.of("technical:recovery"), AccountAccessPolicy.effectivePermissions(developer));
    }
}
