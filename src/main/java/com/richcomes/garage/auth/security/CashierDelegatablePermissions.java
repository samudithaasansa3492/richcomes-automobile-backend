package com.richcomes.garage.auth.security;

import java.util.Set;

/**
 * Explicit actions OWNER may delegate individually to CASHIER.
 * Never delegate employee, account, audit, technical or delegation-management
 * privileges. No unrecognized or future permissions are grantable by default.
 */
public final class CashierDelegatablePermissions {
    private CashierDelegatablePermissions() {}

    public static final Set<String> ALLOWED = Set.of(
            "customer:register", "customer:read", "customer:manage",
            "vehicle:read", "vehicle:create", "vehicle:update",
            "appointment:read", "appointment:create", "appointment:manage",
            "inventory:read", "inventory:create", "inventory:update",
            "service:read", "service:create", "service:update", "service:delete",
            "billing:read", "billing:calculate",
            "report:read", "report:export",
            "history:read", "history:create", "history:update", "history:delete",
            "reminder:read", "reminder:create", "reminder:manage", "reminder:delete",
            "notification:read", "notification:send", "notification:delete"
    );
    public static boolean isDelegatable(String code) {
        return code != null && ALLOWED.contains(code);
    }
}
