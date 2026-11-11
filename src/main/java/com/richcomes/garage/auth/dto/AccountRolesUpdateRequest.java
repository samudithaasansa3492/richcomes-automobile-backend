package com.richcomes.garage.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Replaces the account's entire role set; disable accounts to suspend access. */
public record AccountRolesUpdateRequest(
        @NotEmpty @Size(max = 20)
        Set<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roleCodes
) {
}
