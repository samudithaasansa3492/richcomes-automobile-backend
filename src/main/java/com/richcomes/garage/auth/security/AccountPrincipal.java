package com.richcomes.garage.auth.security;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Detached identity snapshot; contains no password, token or JPA entity. */
public record AccountPrincipal(Long accountId, UUID employeeId, String username,
                               Set<String> roles, Set<String> permissions) implements Principal {
    public AccountPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public static AccountPrincipal from(UserAccount account) {
        return new AccountPrincipal(account.getId(), account.getEmployee().getId(), account.getUsername(),
                account.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                account.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                        .map(permission -> permission.getCode()).collect(Collectors.toSet()));
    }

    @Override
    public String getName() {
        return accountId.toString();
    }
}
