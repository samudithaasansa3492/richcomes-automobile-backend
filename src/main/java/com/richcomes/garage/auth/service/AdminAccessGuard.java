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
                ADMIN_ROLE_NOT_CONFIGURED, "Administrator role has not been configured"));
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
                && account.getRoles().stream().anyMatch(role -> "OWNER_ADMIN".equals(role.getCode()))
                && accounts.countActiveOwners() <= 1) {
            throw new AccountOperationException(LAST_ACTIVE_ADMIN, "At least one active administrator must remain");
        }
    }

    public void protectEmployee(UUID employeeId, Long actorId) {
        accounts.findByEmployee_Id(employeeId).ifPresent(account -> {
            rejectSelf(account.getId(), actorId);
            protectOwner(account);
        });
    }
}
