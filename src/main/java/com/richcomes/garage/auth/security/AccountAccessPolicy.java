package com.richcomes.garage.auth.security;

import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.employee.entity.EmployeePosition;
import com.richcomes.garage.customer.enums.CustomerStatus;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Fail closed across customer, staff and technical identities.
 * Granting a permission does not turn a customer into staff (or vice versa).
 */
public final class AccountAccessPolicy {
    private AccountAccessPolicy() {}
    private static final Set<String> STAFF_ROLES = Set.of("OWNER", "CASHIER", "ADMIN");

    public static boolean canAuthenticate(UserAccount account) {
        if (account == null || !account.isEnabled()) return false;
        if (account.isTechnical()) {
            return account.getEmployee() == null && account.getCustomer() == null
                    && account.getRoles().size() == 1
                    && account.getRoles().stream().allMatch(role -> "ADMIN".equals(role.getCode()));
        }
        if (account.isCustomer()) {
            return account.getEmployee() == null && account.getCustomer() != null
                    && account.getCustomer().getStatus() == CustomerStatus.ACTIVE
                    && account.getRoles().size() == 1
                    && account.getRoles().stream().allMatch(role -> "CUSTOMER".equals(role.getCode()));
        }
        return "BUSINESS".equals(account.getIdentityKind()) && account.getCustomer() == null
                && account.getEmployee() != null && account.getEmployee().isActive()
                && account.getEmployee().getPosition() != EmployeePosition.TECHNICIAN
                && !account.getRoles().isEmpty()
                && account.getRoles().stream().allMatch(role -> STAFF_ROLES.contains(role.getCode()));
    }

    public static Set<String> effectivePermissions(UserAccount account) {
        if (!canAuthenticate(account)) return Set.of();
        if (account.isTechnical()) return Set.of("technical:recovery");
        if (account.isCustomer()) return Set.of("customer:self");
        // No accidental ADMIN privilege escalation to business data.
        if (account.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))) return Set.of();
        var granted = new HashSet<>(account.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .filter(code -> !"technical:recovery".equals(code) && !"customer:self".equals(code))
                .collect(Collectors.toSet()));
        // Enforce OWNER-only delegation even if a database administrator accidentally
        // grants owner permissions to CASHIER's shared role. Do not let role-wide
        // CASHIER permissions bypass the individual cashier delegation table.
        boolean owner = account.getRoles().stream().anyMatch(role -> "OWNER".equals(role.getCode()));
        if (!owner) {
            granted.remove("customer:delegate_register");
            granted.remove("customer:register");
        }
        // Direct permissions never confer role or generic business data access.
        // A cashier receives only the specifically delegated registration action.
        if (account.getRoles().size() == 1 && account.getRoles().stream()
                .allMatch(role -> "CASHIER".equals(role.getCode()))) {
            account.getDirectPermissions().stream()
                    .map(permission -> permission.getCode())
                    .filter("customer:register"::equals)
                    .forEach(granted::add);
        }
        return Set.copyOf(granted);
    }
}
