package com.richcomes.garage.auth.dto;

import jakarta.validation.constraints.NotNull;

public record CustomerRegistrationDelegationRequest(@NotNull Boolean enabled) {}
