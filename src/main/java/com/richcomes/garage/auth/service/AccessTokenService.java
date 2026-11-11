package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.config.JwtSettings;
import com.richcomes.garage.auth.dto.LoginResponse;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final JwtSettings settings;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, JwtSettings settings, Clock authClock) {
        this.encoder = encoder;
        this.settings = settings;
        this.clock = authClock;
    }

    public LoginResponse issue(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Account ID must be positive");
        }
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(settings.issuer())
                .audience(List.of(settings.audience())).subject(accountId.toString())
                .issuedAt(now).notBefore(now).expiresAt(now.plus(JwtSettings.ACCESS_TOKEN_LIFETIME))
                .id(UUID.randomUUID().toString()).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", JwtSettings.ACCESS_TOKEN_LIFETIME.toSeconds());
    }
}
