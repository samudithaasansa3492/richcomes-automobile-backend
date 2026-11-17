package com.richcomes.garage.auth.security;

import java.util.Set;

/**
 * Explicit actions OWNER may delegate individually to CASHIER.
 * Never delegate employee, account, audit, technical or delegation-management
 * privileges. No unrecognized or future permissions are grantable by default.
 */
public final class CashierDelegatablePermissions {
    private CashierDelegatablePermissions() {}

    // Conservative pre-approval policy. Never delegate financial reporting,
    // customer-record browsing/management or destructive DELETE actions.
    // This applies equally to previously persisted direct permission rows:
    // AccountAccessPolicy filters effective permissions using this same set.
    // New or broader actions require explicit security and business approval.
    public static final Set<String> ALLOWED = Set.of(
            "customer:register",
            "vehicle:read", "vehicle:create", "vehicle:update",
            "appointment:read", "appointment:create", "appointment:manage",
            "inventory:read", "inventory:create", "inventory:update",
            "service:read", "service:create", "service:update",
            "billing:calculate",
            "history:read", "history:create", "history:update",
            "reminder:read", "reminder:create", "reminder:manage",
            "notification:read", "notification:send"
    );
    // Disallowed before release review, but older persisted account grants
    // may need to be explicitly revoked with an immutable audit record.
    // They cannot authorize requests, and can never be granted anew.
    public static final Set<String> OWNER_ONLY = Set.of(
            "customer:read", "customer:manage", "billing:read",
            "report:read", "report:export", "service:delete",
            "history:delete", "reminder:delete", "notification:delete"
    );

    public static boolean isDelegatable(String code) {
        return code != null && ALLOWED.contains(code);
    }

    public static boolean isRevocable(String code) {
        return isDelegatable(code) || (code != null && OWNER_ONLY.contains(code));
    }
}
