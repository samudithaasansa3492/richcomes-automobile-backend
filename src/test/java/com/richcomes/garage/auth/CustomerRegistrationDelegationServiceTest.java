package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.PermissionRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.recovery.SecurityAuditRepository;
import com.richcomes.garage.auth.security.AccountAccessPolicy;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.AdminAccessGuard;
import com.richcomes.garage.auth.service.CustomerRegistrationDelegationService;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.entity.EmployeePosition;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerRegistrationDelegationServiceTest {
    @Mock AdminAccessGuard guard;
    @Mock UserAccountRepository accounts;
    @Mock PermissionRepository definitions;
    @Mock SecurityAuditRepository audit;
    private CustomerRegistrationDelegationService service;
    private UserAccount cashier;
    private Permission registration;
    private final AccountPrincipal owner = new AccountPrincipal(40L, null, "owner.one",
            Set.of("OWNER"), Set.of("customer:delegate_register"));

    @BeforeEach void init() {
        service = new CustomerRegistrationDelegationService(guard, accounts, definitions, audit);
        Employee employee = new Employee(); employee.setPosition(EmployeePosition.CASHIER);
        cashier = new UserAccount(employee, "cashier.one", "synthetic-hash");
        cashier.assignRole(new Role("CASHIER", "Cashier"));
        cashier.enable();
        registration = new Permission("customer:register", "Register");
    }

    @Test void authorizedOwnerGrantsAndRevokesSingleCashierPermission() {
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        when(definitions.findByCode("customer:register")).thenReturn(Optional.of(registration));
        assertTrue(service.setAllowed(7L, true, owner).registrationPermitted());
        assertTrue(AccountAccessPolicy.effectivePermissions(cashier).contains("customer:register"));
        assertFalse(service.setAllowed(7L, false, owner).registrationPermitted());
        assertFalse(AccountAccessPolicy.effectivePermissions(cashier).contains("customer:register"));
        verify(guard, times(2)).lockAdministration();
        verify(accounts, times(2)).saveAndFlush(cashier);
    }

    @Test void disabledOrInactiveCashierCannotReceiveARegistrationGrant() {
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        cashier.disable();
        var disabled = assertThrows(AccountOperationException.class,
                () -> service.setAllowed(7L, true, owner));
        assertEquals(AccountOperationException.Code.CASHIER_NOT_ELIGIBLE, disabled.getCode());

        cashier.enable();
        cashier.getEmployee().setActive(false);
        var inactive = assertThrows(AccountOperationException.class,
                () -> service.setAllowed(7L, true, owner));
        assertEquals(AccountOperationException.Code.CASHIER_NOT_ELIGIBLE, inactive.getCode());
        verifyNoInteractions(definitions, audit);
        verify(accounts, never()).saveAndFlush(any());
        assertTrue(cashier.getDirectPermissions().isEmpty());
    }

    @Test void ownerCanRevokeRegistrationFromDisabledCashierWithoutCatalogLookup() {
        cashier.grantDirectPermission(registration);
        cashier.disable();
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        var revoked = service.setAllowed(7L, false, owner);
        assertFalse(revoked.registrationPermitted());
        assertTrue(cashier.getDirectPermissions().isEmpty());
        verifyNoInteractions(definitions);
        verify(accounts).saveAndFlush(cashier);
        verify(audit).save(any());
    }

    @Test void revokingAlreadyAbsentRegistrationIsIdempotent() {
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        assertFalse(service.setAllowed(7L, false, owner).registrationPermitted());
        verifyNoInteractions(definitions, audit);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test void cashierCannotGrantItselfRegistrationAuthority() {
        AccountPrincipal ordinaryCashier = new AccountPrincipal(7L, null, "cashier.one",
                Set.of("CASHIER"), Set.of());
        var problem = assertThrows(AccountOperationException.class,
                () -> service.setAllowed(7L, true, ordinaryCashier));
        assertEquals(AccountOperationException.Code.CUSTOMER_REGISTRATION_NOT_ALLOWED, problem.getCode());
        verifyNoInteractions(guard, accounts, definitions);
    }

    @Test void cannotDelegateToOwnerOrCustomerOrTechnicalIdentity() {
        cashier.assignRole(new Role("OWNER", "Forbidden mixed identity"));
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        var problem = assertThrows(AccountOperationException.class,
                () -> service.setAllowed(7L, true, owner));
        assertEquals(AccountOperationException.Code.CASHIER_NOT_ELIGIBLE, problem.getCode());
        verifyNoInteractions(definitions);
    }
}
