package com.richcomes.garage.auth.recovery;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_challenges", schema = "garage")
public class RecoveryChallenge {
    @Id UUID id;
    @Column(name = "account_id", nullable = false) Long accountId;
    @Column(nullable = false, length = 16) String purpose;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) String tokenHash;
    @Column(nullable = false, length = 254) String email;
    @Column(name = "credential_version", nullable = false) long credentialVersion;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(nullable = false) boolean consumed;
    protected RecoveryChallenge() {}
    RecoveryChallenge(Long accountId, String purpose, String hash, String email, long version, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.accountId = accountId; this.purpose = purpose;
        this.tokenHash = hash; this.email = email; this.credentialVersion = version; this.expiresAt = expiresAt;
    }
}
