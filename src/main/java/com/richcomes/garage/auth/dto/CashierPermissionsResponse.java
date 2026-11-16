package com.richcomes.garage.auth.dto;

import java.util.Set;

/** Only explicit per-CASHIER business grants, never technical/admin secrets. */
public record CashierPermissionsResponse(Long accountId, Set<String> grantedPermissions) {
    public CashierPermissionsResponse {
        grantedPermissions = Set.copyOf(grantedPermissions);
    }
}
