package com.richcomes.garage.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Reference to an independently reviewed in-person verification case.
 * Never submit raw NIC numbers, images, scans, phone numbers or secrets. */
public record CustomerProofRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{7,63}")
        String caseReference) {}
