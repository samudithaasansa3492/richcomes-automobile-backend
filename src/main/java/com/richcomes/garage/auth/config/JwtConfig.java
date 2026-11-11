package com.richcomes.garage.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSettings.class)
public class JwtConfig {

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    public KeyPair jwtSigningKeys(JwtSettings settings) throws IOException, GeneralSecurityException {
        try (var publicStream = settings.publicKey().getInputStream();
             var privateStream = settings.privateKey().getInputStream()) {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicStream);
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateStream);
            if (publicKey == null || privateKey == null || publicKey.getModulus().bitLength() < 2048
                    || !publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new IllegalStateException("JWT requires a matching RSA key pair of at least 2048 bits");
            }
            byte[] probe = "richcomes-jwt-key-pair-check".getBytes(StandardCharsets.US_ASCII);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(probe);
            if (!signature.verify(signed)) {
                throw new IllegalStateException("JWT public and private keys do not form a valid pair");
            }
            return new KeyPair(publicKey, privateKey);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtSigningKeys) {
        var rsa = new RSAKey.Builder((RSAPublicKey) jwtSigningKeys.getPublic())
                .privateKey((RSAPrivateKey) jwtSigningKeys.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair jwtSigningKeys, JwtSettings settings, Clock authClock) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtSigningKeys.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        // Zero skew: a token is unusable once its configured expiration is reached.
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(authClock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            var issued = jwt.getIssuedAt();
            var expires = jwt.getExpiresAt();
            boolean validSubject;
            try {
                validSubject = jwt.getSubject() != null && jwt.getSubject().matches("[1-9][0-9]*")
                        && Long.parseLong(jwt.getSubject()) > 0;
            } catch (NumberFormatException exception) {
                validSubject = false;
            }
            boolean valid = validSubject && jwt.getAudience() != null
                    && jwt.getAudience().contains(settings.audience())
                    && issued != null && expires != null && jwt.getNotBefore() != null
                    && expires.isAfter(authClock.instant()) && !issued.isAfter(authClock.instant())
                    && expires.isAfter(issued)
                    && Duration.between(issued, expires).compareTo(JwtSettings.ACCESS_TOKEN_LIFETIME) <= 0
                    && jwt.getId() != null && !jwt.getId().isBlank();
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid access token", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(settings.issuer()), claims));
        return decoder;
    }
}
