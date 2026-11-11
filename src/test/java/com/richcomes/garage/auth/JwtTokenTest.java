package com.richcomes.garage.auth;

import com.richcomes.garage.auth.config.JwtConfig;
import com.richcomes.garage.auth.config.JwtSettings;
import com.richcomes.garage.auth.service.AccessTokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenTest {
    static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static KeyPair keys;
    private static JwtEncoder encoder;
    private static JwtDecoder decoder;
    private static JwtSettings settings;
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeAll
    static void setUp() throws Exception {
        keys = newKeys();
        settings = new JwtSettings("https://test.invalid", "richcomes-api", null, null);
        var config = new JwtConfig();
        encoder = config.jwtEncoder(keys);
        decoder = config.jwtDecoder(keys, settings, CLOCK);
    }

    static KeyPair newKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Test
    void issuesRs256TokenWithFifteenMinuteLifetimeAndMinimalClaims() {
        var response = new AccessTokenService(encoder, settings, CLOCK).issue(42L);
        Jwt decoded = decoder.decode(response.accessToken());
        assertEquals("RS256", decoded.getHeaders().get("alg"));
        assertEquals("42", decoded.getSubject());
        assertEquals(NOW.plusSeconds(900), decoded.getExpiresAt());
        assertEquals(900, response.expiresIn());
        assertEquals("Bearer", response.tokenType());
        assertNotNull(decoded.getId());
        assertFalse(decoded.getClaims().containsKey("password"));
        assertFalse(decoded.getClaims().containsKey("roles"));
        assertFalse(response.toString().contains(response.accessToken()));
    }

    @Test
    void rejectsExpiredToken() {
        reject(claims().issuedAt(NOW.minusSeconds(901)).notBefore(NOW.minusSeconds(901)).expiresAt(NOW.minusSeconds(1)));
    }

    @Test
    void rejectsTokenAtExactExpiry() {
        reject(claims().issuedAt(NOW.minusSeconds(900)).notBefore(NOW.minusSeconds(900)).expiresAt(NOW));
    }

    @Test
    void rejectsFutureToken() {
        reject(claims().issuedAt(NOW.plusSeconds(10)).notBefore(NOW.plusSeconds(10)));
    }

    @Test
    void rejectsWrongIssuer() {
        reject(claims().issuer("https://wrong.invalid"));
    }

    @Test
    void rejectsWrongAudience() {
        reject(claims().audience(List.of("another-api")));
    }

    @Test
    void rejectsMissingAudience() {
        reject(claims().claims(values -> values.remove("aud")));
    }

    @Test
    void rejectsMissingExpiry() {
        reject(claims().claims(values -> values.remove("exp")));
    }

    @Test
    void rejectsInvalidAndOverflowingSubjects() {
        for (String subject : List.of("abc", "0", "-1", "999999999999999999999999999")) {
            reject(claims().subject(subject));
        }
    }

    @Test
    void rejectsLongLivedToken() {
        reject(claims().expiresAt(NOW.plusSeconds(901)));
    }

    @Test
    void rejectsWrongSignatureAndAlgorithm() throws Exception {
        JwtEncoder other = new JwtConfig().jwtEncoder(newKeys());
        String wrongKey = other.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims().build())).getTokenValue();
        assertThrows(JwtException.class, () -> decoder.decode(wrongKey));
        String wrongAlgorithm = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS512).build(), claims().build())).getTokenValue();
        assertThrows(JwtException.class, () -> decoder.decode(wrongAlgorithm));
    }

    @Test
    void loadsExternalPemKeysAndRejectsMismatchedPair() throws Exception {
        var valid = new JwtSettings("issuer", "audience", pem("PUBLIC KEY", keys.getPublic().getEncoded()),
                pem("PRIVATE KEY", keys.getPrivate().getEncoded()));
        var loaded = new JwtConfig().jwtSigningKeys(valid);
        assertArrayEquals(keys.getPublic().getEncoded(), loaded.getPublic().getEncoded());
        var invalid = new JwtSettings("issuer", "audience", valid.publicKey(),
                pem("PRIVATE KEY", newKeys().getPrivate().getEncoded()));
        assertThrows(IllegalStateException.class, () -> new JwtConfig().jwtSigningKeys(invalid));
    }

    private static ByteArrayResource pem(String type, byte[] encoded) {
        String text = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(text.getBytes(StandardCharsets.US_ASCII));
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(settings.issuer()).audience(List.of(settings.audience()))
                .subject("42").issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(900)).id("test-id");
    }

    private void reject(JwtClaimsSet.Builder claims) {
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
}
