package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.CustomerRegistrationDelegationResponse;
import com.richcomes.garage.auth.entity.UserAccount;
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

    public CustomerRegistrationDelegationService(AdminAccessGuard administration,
            UserAccountRepository accounts, PermissionRepository permissions) {
        this.administration = administration;
        this.accounts = accounts;
        this.permissions = permissions;
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

        var permission = permissions.findByCode("customer:register")
                .orElseThrow(() -> failure(CUSTOMER_ROLE_NOT_CONFIGURED, "Customer registration permission not configured"));
        if (allowed) {
            cashier.grantDirectPermission(permission);
        } else {
            cashier.revokeDirectPermission(permission);
        }
        accounts.saveAndFlush(cashier);
        return new CustomerRegistrationDelegationResponse(cashierAccountId, allowed);
    }

    private AccountOperationException failure(AccountOperationException.Code code, String message) {
        return new AccountOperationException(code, message);
    }
}
