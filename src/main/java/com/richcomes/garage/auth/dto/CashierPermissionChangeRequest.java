package com.richcomes.garage.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CashierPermissionChangeRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]*:[a-z][a-z0-9_]*")
        String permissionCode,
        @NotNull Boolean enabled
) {}
