package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.service.AdminAccessGuard;
import com.richcomes.garage.employee.entity.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;

class AdminAccessGuardTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AdminAccessGuard guard = new AdminAccessGuard(roles, accounts);

    @Test void failsClosedWithoutOwnerRole() {
        assertEquals(OWNER_ROLE_NOT_CONFIGURED,
                assertThrows(AccountOperationException.class, guard::lockAdministration).getCode());
    }
    @Test void acquiresSharedRoleLock() {
        when(roles.lockOwnerRole()).thenReturn(Optional.of(new Role("OWNER", "Admin")));
        guard.lockAdministration();
        verify(roles).lockOwnerRole();
    }
    @Test void rejectsSelfAndMissingActor() {
        assertEquals(SELF_MODIFICATION, assertThrows(AccountOperationException.class,
                () -> guard.rejectSelf(7L, 7L)).getCode());
        assertEquals(INVALID_REQUEST, assertThrows(AccountOperationException.class,
                () -> guard.rejectSelf(7L, null)).getCode());
        assertDoesNotThrow(() -> guard.rejectSelf(7L, 8L));
    }
    @Test void rejectsRemovalOfLastActiveOwner() {
        when(accounts.countActiveOwners()).thenReturn(1L);
        assertEquals(LAST_ACTIVE_OWNER, assertThrows(AccountOperationException.class,
                () -> guard.protectOwner(owner())).getCode());
    }
    @Test void allowsRemovalWhenAnotherOwnerRemains() {
        when(accounts.countActiveOwners()).thenReturn(2L);
        assertDoesNotThrow(() -> guard.protectOwner(owner()));
    }
    @Test void disabledOrInactiveOwnersDoNotCountAsActive() {
        var account = owner();
        account.disable();
        guard.protectOwner(account);
        account.enable();
        account.getEmployee().setActive(false);
        guard.protectOwner(account);
        verify(accounts, never()).countActiveOwners();
    }
    @Test void employeeDeactivationCannotBypassLastOwnerCheck() {
        var id = UUID.randomUUID();
        when(accounts.findByEmployee_Id(id)).thenReturn(Optional.of(owner()));
        when(accounts.countActiveOwners()).thenReturn(1L);
        assertEquals(LAST_ACTIVE_OWNER, assertThrows(AccountOperationException.class,
                () -> guard.protectEmployee(id, 99L)).getCode());
    }
    @Test void employeeDeactivationCannotDisableOwnAccount() {
        var id = UUID.randomUUID();
        when(accounts.findByEmployee_Id(id)).thenReturn(Optional.of(owner()));
        assertEquals(SELF_MODIFICATION, assertThrows(AccountOperationException.class,
                () -> guard.protectEmployee(id, 7L)).getCode());
    }
    @Test void technicianPositionChangeIsBlockedForAnyAccount() {
        var id = UUID.randomUUID();
        when(accounts.findByEmployee_Id(id)).thenReturn(Optional.of(owner()));
        assertEquals(INVALID_REQUEST, assertThrows(AccountOperationException.class,
                () -> guard.rejectTechnicianWithAccount(id)).getCode());
    }

    @Test void technicianWithoutAccountRemainsAnEmployee() {
        assertDoesNotThrow(() -> guard.rejectTechnicianWithAccount(UUID.randomUUID()));
    }

    @Test void developerRoleIsNotAnOwner() {
        var account = owner();
        account.getRoles().stream().toList().forEach(account::removeRole);
        account.assignRole(new Role("ADMIN", "Developer"));
        guard.protectOwner(account);
        verify(accounts, never()).countActiveOwners();
    }

    @Test void technicalAccountsCannotBeChangedThroughEmployeeEndpoints() {
        var id = UUID.randomUUID();
        var technical = owner();
        technical.assignRole(new Role("ADMIN", "Developer"));
        when(accounts.findByEmployee_Id(id)).thenReturn(Optional.of(technical));
        assertEquals(TECHNICAL_ACCOUNT_PROTECTED, assertThrows(AccountOperationException.class,
                () -> guard.rejectTechnicalEmployeeMutation(id)).getCode());
        assertEquals(TECHNICAL_ACCOUNT_PROTECTED, assertThrows(AccountOperationException.class,
                () -> guard.protectEmployee(id, 99L)).getCode());
        verify(accounts, never()).countActiveOwners();
    }

    private UserAccount owner() {
        var account = new UserAccount(new Employee(), "owner.one", "encoded-test-hash");
        ReflectionTestUtils.setField(account, "id", 7L);
        account.assignRole(new Role("OWNER", "Admin"));
        account.enable();
        return account;
    }
}
