package com.richcomes.garage.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Trusted staff enroll an existing, identity-checked customer. Never a public signup route. */
public record CustomerAccountCreateRequest(
        @NotNull @Positive Long customerId,
        @NotBlank @Size(min = 3, max = 80) String username,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 15, max = 72) String password) {
    @Override public String toString() {
        return "CustomerAccountCreateRequest[customerId=" + customerId
                + ", username=" + username + ", password=[REDACTED]]";
    }
}
