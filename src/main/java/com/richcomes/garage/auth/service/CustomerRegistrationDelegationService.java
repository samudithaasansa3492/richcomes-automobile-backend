package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.CustomerRegistrationDelegationResponse;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.recovery.SecurityAuditEvent;
import com.richcomes.garage.auth.recovery.SecurityAuditRepository;
import java.time.Instant;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.PermissionRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;

/**
 * OWNER may grant/revoke customer registration for exactly one eligible CASHIER
 * without granting it to the entire CASHIER role or relaxing JWT authorization.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CustomerRegistrationDelegationService {
    private final AdminAccessGuard administration;
    private final UserAccountRepository accounts;
    private final PermissionRepository permissions;
    private final SecurityAuditRepository audit;

    public CustomerRegistrationDelegationService(AdminAccessGuard administration,
            UserAccountRepository accounts, PermissionRepository permissions,
            SecurityAuditRepository audit) {
        this.administration = administration;
        this.accounts = accounts;
        this.permissions = permissions;
        this.audit = audit;
    }

    public CustomerRegistrationDelegationResponse setAllowed(Long cashierAccountId, boolean allowed,
            AccountPrincipal actor) {
        if (actor == null || actor.accountId() == null
                || !actor.roles().contains("OWNER")
                || !actor.permissions().contains("customer:delegate_register")) {
            throw failure(CUSTOMER_REGISTRATION_NOT_ALLOWED, "Only an authorized OWNER may delegate registration");
        }
        if (cashierAccountId == null || cashierAccountId <= 0) {
            throw failure(INVALID_REQUEST, "Valid cashier account ID required");
        }
        // Same lock as business role replacement and account disablement.
        // Role and direct-grant changes therefore cannot race.
        administration.lockAdministration();
        var cashier = accounts.findOneById(cashierAccountId)
                .orElseThrow(() -> failure(ACCOUNT_NOT_FOUND, "Account not found"));
        if (!"BUSINESS".equals(cashier.getIdentityKind())
                || cashier.getEmployee() == null || cashier.getCustomer() != null
                || cashier.getRoles().size() != 1
                || cashier.getRoles().stream().noneMatch(role -> "CASHIER".equals(role.getCode()))) {
            throw failure(CASHIER_NOT_ELIGIBLE, "Only a CASHIER account may receive registration permission");
        }

        // Disabled or inactive staff must never be pre-granted registration:
        // otherwise enabling the same account could silently restore access.
        if (allowed && (!cashier.isEnabled() || !cashier.getEmployee().isActive())) {
            throw failure(CASHIER_NOT_ELIGIBLE,
                    "Only an active, enabled CASHIER may receive registration permission");
        }
        var existingGrant = cashier.getDirectPermissions().stream()
                .filter(existing -> "customer:register".equals(existing.getCode()))
                .findFirst();
        if (existingGrant.isPresent() != allowed) {
            if (allowed) {
                var permission = permissions.findByCode("customer:register")
                        .orElseThrow(() -> failure(CUSTOMER_ROLE_NOT_CONFIGURED,
                                "Customer registration permission not configured"));
                cashier.grantDirectPermission(permission);
            } else {
                // Revoke even on inactive staff; removing a grant does not need a
                // fresh catalog lookup and is idempotent.
                cashier.revokeDirectPermission(existingGrant.orElseThrow());
            }
            accounts.saveAndFlush(cashier);
            audit.save(new SecurityAuditEvent(actor.accountId(), cashierAccountId,
                    allowed ? "CASHIER_REGISTRATION_GRANTED" : "CASHIER_REGISTRATION_REVOKED",
                    Instant.now()));
        }
        return new CustomerRegistrationDelegationResponse(cashierAccountId, allowed);
    }

    private AccountOperationException failure(AccountOperationException.Code code, String message) {
        return new AccountOperationException(code, message);
    }
}
