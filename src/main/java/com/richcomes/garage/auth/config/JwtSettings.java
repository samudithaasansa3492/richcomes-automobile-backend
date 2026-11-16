package com.richcomes.garage.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("auth.jwt")
public record JwtSettings(@NotBlank String issuer, @NotBlank String audience,
                          @NotNull Resource publicKey, @NotNull Resource privateKey) {
    public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
}
