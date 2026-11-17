package com.richcomes.garage.auth.security;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Detached identity snapshot; contains no password, token or JPA entity. */
public record AccountPrincipal(Long accountId, UUID employeeId, String username,
                               Set<String> roles, Set<String> permissions, Long customerId) implements Principal {
    public AccountPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    /** Backwards-compatible construction used by existing staff-only tests. */
    public AccountPrincipal(Long accountId, UUID employeeId, String username,
                            Set<String> roles, Set<String> permissions) {
        this(accountId, employeeId, username, roles, permissions, null);
    }

    public static AccountPrincipal from(UserAccount account) {
        return new AccountPrincipal(account.getId(), account.getEmployee() == null ? null : account.getEmployee().getId(), account.getUsername(),
                account.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                AccountAccessPolicy.effectivePermissions(account),
                account.getCustomer() == null ? null : account.getCustomer().getId());
    }

    @Override
    public String getName() {
        return accountId.toString();
    }
}
