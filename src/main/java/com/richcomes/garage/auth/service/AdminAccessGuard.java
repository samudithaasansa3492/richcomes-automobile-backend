package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;

/** All access-removal paths must lock the same stable role row before loading targets. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AdminAccessGuard {
    private final RoleRepository roles;
    private final UserAccountRepository accounts;

    public AdminAccessGuard(RoleRepository roles, UserAccountRepository accounts) {
        this.roles = roles;
        this.accounts = accounts;
    }

    public void lockAdministration() {
        roles.lockOwnerRole().orElseThrow(() -> new AccountOperationException(
                OWNER_ROLE_NOT_CONFIGURED, "Owner role has not been configured"));
    }

    public void rejectSelf(Long targetId, Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new AccountOperationException(INVALID_REQUEST, "Authenticated account is required");
        }
        if (actorId.equals(targetId)) {
            throw new AccountOperationException(SELF_MODIFICATION, "Cannot remove your own account access");
        }
    }

    public void protectOwner(UserAccount account) {
        if (account.isEnabled() && account.getEmployee().isActive()
                && account.getRoles().stream().anyMatch(role -> "OWNER".equals(role.getCode()))
                && accounts.countActiveOwners() <= 1) {
            throw new AccountOperationException(LAST_ACTIVE_OWNER, "At least one active owner must remain");
        }
    }

    public void rejectTechnicianWithAccount(UUID employeeId) {
        if (accounts.findByEmployee_Id(employeeId).isPresent()) {
            throw new AccountOperationException(INVALID_REQUEST,
                    "An employee with a system account cannot become a technician");
        }
    }

    public void rejectTechnicalEmployeeMutation(UUID employeeId) {
        accounts.findByEmployee_Id(employeeId).ifPresent(this::rejectTechnicalAccount);
    }

    private void rejectTechnicalAccount(UserAccount account) {
        if (account.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))) {
            throw new AccountOperationException(TECHNICAL_ACCOUNT_PROTECTED,
                    "Technical identities cannot be changed through employee APIs");
        }
    }

    public void protectEmployee(UUID employeeId, Long actorId) {
        accounts.findByEmployee_Id(employeeId).ifPresent(account -> {
            rejectTechnicalAccount(account);
            rejectSelf(account.getId(), actorId);
            protectOwner(account);
        });
    }
}
