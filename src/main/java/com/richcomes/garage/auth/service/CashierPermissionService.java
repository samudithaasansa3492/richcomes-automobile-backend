package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.CashierPermissionChangeRequest;
import com.richcomes.garage.auth.dto.CashierPermissionsResponse;
import com.richcomes.garage.auth.entity.AccountPermissionAuditEvent;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.AccountPermissionAuditRepository;
import com.richcomes.garage.auth.repository.PermissionRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.security.CashierDelegatablePermissions;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;

/**
 * Changes only one exact CASHIER account, with a role-row serialization lock and
 * an immutable audit row in the same database transaction.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CashierPermissionService {
    private final AdminAccessGuard administration;
    private final UserAccountRepository accounts;
    private final PermissionRepository permissions;
    private final AccountPermissionAuditRepository audit;

    public CashierPermissionService(AdminAccessGuard administration,
            UserAccountRepository accounts, PermissionRepository permissions,
            AccountPermissionAuditRepository audit) {
        this.administration = administration;
        this.accounts = accounts;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CashierPermissionsResponse list(Long accountId, AccountPrincipal actor) {
        requireOwner(actor);
        return response(findCashier(accountId));
    }

    public CashierPermissionsResponse change(Long accountId, CashierPermissionChangeRequest request,
            AccountPrincipal actor) {
        requireOwner(actor);
        // Only approved actions can be newly granted. Inert high-impact
        // grants left by an earlier policy may still be revoked and audited.
        if (request == null || request.enabled() == null
                || (request.enabled()
                    ? !CashierDelegatablePermissions.isDelegatable(request.permissionCode())
                    : !CashierDelegatablePermissions.isRevocable(request.permissionCode()))) {
            throw failure(INVALID_REQUEST, "Permission is not eligible for cashier delegation");
        }
        // Grant requires that OWNER possesses the permission. Revocation must
        // remain possible even when OWNER's own business grant was later removed.
        if (request.enabled() && !actor.permissions().contains(request.permissionCode())) {
            throw failure(PERMISSION_DELEGATION_FORBIDDEN, "OWNER cannot delegate an unassigned permission");
        }
        // Serialize against account disablement, role changes, and separate
        // customer-registration delegation endpoints.
        administration.lockAdministration();
        var cashier = findCashier(accountId);
        if (request.enabled() && (!cashier.isEnabled() || !cashier.getEmployee().isActive())) {
            throw failure(CASHIER_NOT_ELIGIBLE, "Only an active CASHIER can receive permissions");
        }
        var existingGrant = cashier.getDirectPermissions().stream()
                .filter(permission -> request.permissionCode().equals(permission.getCode()))
                .findFirst();
        if (existingGrant.isPresent() != request.enabled()) {
            if (request.enabled()) {
                var definition = permissions.findByCode(request.permissionCode())
                        .orElseThrow(() -> failure(INVALID_REQUEST, "Unknown business permission"));
                cashier.grantDirectPermission(definition);
            } else {
                // Revoking an existing grant does not require a redundant
                // permission-catalog lookup. Idempotent revocation is a no-op.
                cashier.revokeDirectPermission(existingGrant.orElseThrow());
            }
            accounts.saveAndFlush(cashier);
            audit.saveAndFlush(new AccountPermissionAuditEvent(
                    actor.accountId(), cashier.getId(), request.permissionCode(),
                    request.enabled(), Instant.now()));
        }
        return response(cashier);
    }

    private UserAccount findCashier(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw failure(INVALID_REQUEST, "Valid cashier account ID required");
        }
        var target = accounts.findOneById(accountId)
                .orElseThrow(() -> failure(ACCOUNT_NOT_FOUND, "Cashier account not found"));
        if (!"BUSINESS".equals(target.getIdentityKind()) || target.getCustomer() != null
                || target.getEmployee() == null || target.getRoles().size() != 1
                || target.getRoles().stream().noneMatch(role -> "CASHIER".equals(role.getCode()))) {
            throw failure(CASHIER_NOT_ELIGIBLE, "Permissions may be assigned only to CASHIER accounts");
        }
        return target;
    }

    private CashierPermissionsResponse response(UserAccount account) {
        return new CashierPermissionsResponse(account.getId(), account.getDirectPermissions()
                .stream().map(p -> p.getCode())
                .filter(CashierDelegatablePermissions::isDelegatable)
                .collect(Collectors.toUnmodifiableSet()));
    }

    private void requireOwner(AccountPrincipal actor) {
        if (actor == null || actor.accountId() == null
                || !actor.roles().contains("OWNER")
                || !actor.permissions().contains("permission:delegate")) {
            throw failure(PERMISSION_DELEGATION_FORBIDDEN, "Only OWNER can manage cashier permissions");
        }
    }

    private AccountOperationException failure(AccountOperationException.Code code, String message) {
        return new AccountOperationException(code, message);
    }
}
