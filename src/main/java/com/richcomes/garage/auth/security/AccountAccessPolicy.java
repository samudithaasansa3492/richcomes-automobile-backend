package com.richcomes.garage.auth.security;

import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.employee.entity.EmployeePosition;
import java.util.Set;
import java.util.stream.Collectors;

/** Fail closed for legacy roles; developer accounts never inherit business permissions. */
public final class AccountAccessPolicy {
    private AccountAccessPolicy() {}
    private static final Set<String> SYSTEM_ROLES = Set.of("OWNER", "CASHIER", "ADMIN");

    public static boolean canAuthenticate(UserAccount account) {
        return account.isEnabled() && account.getEmployee() != null && account.getEmployee().isActive()
                && account.getEmployee().getPosition() != EmployeePosition.TECHNICIAN
                && !account.getRoles().isEmpty()
                && account.getRoles().stream().allMatch(role -> SYSTEM_ROLES.contains(role.getCode()));
    }

    public static Set<String> effectivePermissions(UserAccount account) {
        // Technical operations will have a separate, reviewed allowlist and endpoints.
        // No wildcard/prefix grant, even if ADMIN is accidentally mixed with OWNER.
        if (account.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))) return Set.of();
        return account.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode()).collect(Collectors.toUnmodifiableSet());
    }
}
