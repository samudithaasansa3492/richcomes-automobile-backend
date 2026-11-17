package com.richcomes.garage.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Staff creates a disabled identity only; staff NEVER chooses the password. */
public record CustomerAccountCreateRequest(
        @NotNull @Positive Long customerId,
        @NotBlank @Size(min = 3, max = 80) String username) {}
