package com.richcomes.garage.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record AccountCreateRequest(
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 100) String username,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 15, max = 72) String password,
        @NotEmpty @Size(max = 20)
        Set<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roleCodes
) {
    @Override
    public String toString() {
        return "AccountCreateRequest[employeeId=" + employeeId + ", username=" + username
                + ", password=[REDACTED], roleCodes=" + roleCodes + "]";
    }
}
