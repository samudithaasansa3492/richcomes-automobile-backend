package com.richcomes.garage.auth.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AccountResponse(
        Long id,
        UUID employeeId,
        String username,
        boolean enabled,
        Set<String> roleCodes,
        Instant createdAt,
        Instant updatedAt
) {
    public AccountResponse {
        roleCodes = Set.copyOf(roleCodes);
    }
}
