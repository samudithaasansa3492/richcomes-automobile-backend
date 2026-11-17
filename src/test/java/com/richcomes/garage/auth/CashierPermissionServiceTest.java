package com.richcomes.garage.auth;

import com.richcomes.garage.auth.dto.CashierPermissionChangeRequest;
import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.AccountPermissionAuditRepository;
import com.richcomes.garage.auth.repository.PermissionRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountAccessPolicy;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.AdminAccessGuard;
import com.richcomes.garage.auth.service.CashierPermissionService;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.entity.EmployeePosition;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashierPermissionServiceTest {
    @Mock AdminAccessGuard guard;
    @Mock UserAccountRepository accounts;
    @Mock PermissionRepository definitions;
    @Mock AccountPermissionAuditRepository audit;
    private CashierPermissionService service;
    private UserAccount cashier;
    private final AccountPrincipal owner = new AccountPrincipal(99L, null, "owner.one",
            Set.of("OWNER"), Set.of("permission:delegate", "vehicle:read", "vehicle:update"));

    @BeforeEach void setup() {
        service = new CashierPermissionService(guard, accounts, definitions, audit);
        var employee = new Employee();
        employee.setPosition(EmployeePosition.CASHIER);
        cashier = new UserAccount(employee, "cashier.one", "dummy-bcrypt-hash");
        cashier.assignRole(new Role("CASHIER", "Cashier"));
        cashier.enable();
        ReflectionTestUtils.setField(cashier, "id", 7L);
    }

    @Test void ownerGrantsOneActionAndRevokesItWithExactAuditRecords() {
        var permission = new Permission("vehicle:read", "Read vehicles");
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        when(definitions.findByCode("vehicle:read")).thenReturn(Optional.of(permission));
        var request = new CashierPermissionChangeRequest("vehicle:read", true);
        var granted = service.change(7L, request, owner);
        assertEquals(Set.of("vehicle:read"), granted.grantedPermissions());
        assertTrue(AccountAccessPolicy.effectivePermissions(cashier).contains("vehicle:read"));

        // Idempotent repeat: no second DB audit event.
        service.change(7L, request, owner);
        assertEquals(Set.of("vehicle:read"), cashier.getDirectPermissions().stream()
                .map(Permission::getCode).collect(java.util.stream.Collectors.toSet()));

        var removed = service.change(7L,
                new CashierPermissionChangeRequest("vehicle:read", false), owner);
        assertTrue(removed.grantedPermissions().isEmpty());
        assertFalse(AccountAccessPolicy.effectivePermissions(cashier).contains("vehicle:read"));
        verify(guard, times(3)).lockAdministration();
        verify(accounts, times(2)).saveAndFlush(cashier);
        var captor = ArgumentCaptor.forClass(
                com.richcomes.garage.auth.entity.AccountPermissionAuditEvent.class);
        verify(audit, times(2)).saveAndFlush(captor.capture());
        assertEquals(99L, captor.getAllValues().get(0).getActorId());
        assertEquals(7L, captor.getAllValues().get(0).getTargetAccountId());
        assertEquals("vehicle:read", captor.getAllValues().get(0).getPermissionCode());
        assertTrue(captor.getAllValues().get(0).isGranted());
        assertFalse(captor.getAllValues().get(1).isGranted());
    }

    @Test void ordinaryCashierCannotDelegateEvenWithForgedPermission() {
        var notOwner = new AccountPrincipal(7L, null, "cashier.one",
                Set.of("CASHIER"), Set.of("permission:delegate"));
        var error = assertThrows(AccountOperationException.class,
                () -> service.change(7L,
                        new CashierPermissionChangeRequest("vehicle:read", true), notOwner));
        assertEquals(AccountOperationException.Code.PERMISSION_DELEGATION_FORBIDDEN, error.getCode());
        verifyNoInteractions(guard, accounts, definitions, audit);
    }

    @Test void securityAdministrativeAndUnknownPermissionsCannotBeDelegated() {
        for (var code : Set.of("audit:read", "account:manage", "employee:create",
                "technical:recovery", "permission:delegate", "super:power",
                "customer:read", "customer:manage", "service:delete",
                "billing:read", "report:read", "report:export",
                "history:delete", "reminder:delete", "notification:delete")) {
            var error = assertThrows(AccountOperationException.class,
                    () -> service.change(7L, new CashierPermissionChangeRequest(code, true), owner));
            assertEquals(AccountOperationException.Code.INVALID_REQUEST, error.getCode());
        }
        verifyNoInteractions(guard, accounts, definitions, audit);
    }

    @Test void ownerCannotDelegateBusinessPermissionTheyDoNotPossess() {
        var error = assertThrows(AccountOperationException.class,
                () -> service.change(7L,
                        new CashierPermissionChangeRequest("inventory:read", true), owner));
        assertEquals(AccountOperationException.Code.PERMISSION_DELEGATION_FORBIDDEN, error.getCode());
        verifyNoInteractions(guard, accounts, definitions, audit);
    }

    @Test void targetMustBeAnActualCashierNotMixedOwner() {
        cashier.assignRole(new Role("OWNER", "Owner"));
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        var error = assertThrows(AccountOperationException.class,
                () -> service.list(7L, owner));
        assertEquals(AccountOperationException.Code.CASHIER_NOT_ELIGIBLE, error.getCode());
        verifyNoInteractions(audit);
    }

    @Test void ownerMayRevokeExistingGrantEvenWhenOwnBusinessRightHasBeenRemoved() {
        var permission = new Permission("vehicle:read", "Read vehicles");
        cashier.grantDirectPermission(permission);
        cashier.disable();
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        // OWNER keeps delegated-management authority but no longer has vehicle:read.
        var revokingOwner = new AccountPrincipal(99L, null, "owner.one",
                Set.of("OWNER"), Set.of("permission:delegate"));
        var result = service.change(7L,
                new CashierPermissionChangeRequest("vehicle:read", false), revokingOwner);
        assertTrue(result.grantedPermissions().isEmpty());
        assertFalse(cashier.getDirectPermissions().contains(permission));
        verify(definitions, never()).findByCode(anyString());
        verify(accounts).saveAndFlush(cashier);
        var capture = ArgumentCaptor.forClass(
                com.richcomes.garage.auth.entity.AccountPermissionAuditEvent.class);
        verify(audit).saveAndFlush(capture.capture());
        assertEquals("vehicle:read", capture.getValue().getPermissionCode());
        assertFalse(capture.getValue().isGranted());
    }

    @Test void ownerCanAuditRevokePreviouslyPersistedOwnerOnlyGrantWithoutRegranting() {
        var legacy = new Permission("report:read", "Unapproved historical grant");
        cashier.grantDirectPermission(legacy);
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        var ownerWithDelegationOnly = new AccountPrincipal(99L, null, "owner.one",
                Set.of("OWNER"), Set.of("permission:delegate"));

        assertFalse(AccountAccessPolicy.effectivePermissions(cashier).contains("report:read"));

        var result = service.change(7L,
                new CashierPermissionChangeRequest("report:read", false),
                ownerWithDelegationOnly);
        assertTrue(result.grantedPermissions().isEmpty());
        assertTrue(cashier.getDirectPermissions().isEmpty());
        verify(definitions, never()).findByCode(anyString());
        verify(accounts).saveAndFlush(cashier);
        var captured = ArgumentCaptor.forClass(
                com.richcomes.garage.auth.entity.AccountPermissionAuditEvent.class);
        verify(audit).saveAndFlush(captured.capture());
        assertEquals("report:read", captured.getValue().getPermissionCode());
        assertFalse(captured.getValue().isGranted());

        // The cleanup request is idempotent and cannot regrant report access.
        assertTrue(service.change(7L,
                new CashierPermissionChangeRequest("report:read", false),
                ownerWithDelegationOnly).grantedPermissions().isEmpty());
        verify(audit, times(1)).saveAndFlush(any());
    }

    @Test void disabledCashierCanHavePermissionRevokedButNotGranted() {
        cashier.disable();
        when(accounts.findOneById(7L)).thenReturn(Optional.of(cashier));
        var error = assertThrows(AccountOperationException.class,
                () -> service.change(7L,
                        new CashierPermissionChangeRequest("vehicle:read", true), owner));
        assertEquals(AccountOperationException.Code.CASHIER_NOT_ELIGIBLE, error.getCode());
        assertTrue(service.change(7L,
                new CashierPermissionChangeRequest("vehicle:read", false), owner)
                .grantedPermissions().isEmpty());
    }
}
