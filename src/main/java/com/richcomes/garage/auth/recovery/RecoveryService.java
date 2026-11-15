package com.richcomes.garage.auth.recovery;

import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountAccessPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class RecoveryService {
    private final UserAccountRepository accounts;
    private final RecoveryChallengeRepository challenges;
    private final SecurityAuditRepository audit;
    private final PasswordEncoder encoder;
    private final RecoveryDelivery delivery;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();
    public RecoveryService(UserAccountRepository accounts, RecoveryChallengeRepository challenges,
            SecurityAuditRepository audit, PasswordEncoder encoder, RecoveryDelivery delivery, Clock authClock,
            jakarta.persistence.EntityManager entityManager) {
        this.accounts = accounts; this.challenges = challenges; this.audit = audit;
        this.encoder = encoder; this.delivery = delivery; this.clock = authClock;
        this.entityManager = entityManager;
    }

    public void enroll(Long accountId, String password, String email) {
        if (!delivery.available()) throw new RecoveryFailure();
        var account = lock(accountId);
        if (!AccountAccessPolicy.canAuthenticate(account) || !validPasswordInput(password)
                || !encoder.matches(password, account.getPasswordHash())) throw new RecoveryFailure();
        if (!canSend(account)) throw new RecoveryFailure();
        issue(account, "VERIFY_EMAIL", email, accountId);
    }

    /** Same response for absent, disabled, unverified and throttled accounts; never returns a secret. */
    public void request(String username, Long actorId) {
        if (!delivery.available()) return;
        String normalized;
        try { normalized = UserAccount.normalizeUsername(username); }
        catch (IllegalArgumentException exception) { return; }
        var found = accounts.findByUsername(normalized);
        if (found.isEmpty()) return;
        var account = lock(found.get().getId());
        if (account == null || !AccountAccessPolicy.canAuthenticate(account)
                || account.getRecoveryEmail() == null || !canSend(account)) return;
        // Only send to the address previously confirmed by its recipient, never an admin-supplied address.
        issue(account, "RESET_PASSWORD", account.getRecoveryEmail(), actorId);
    }

    public void verifyEmail(String token) {
        var challenge = redeem(token, "VERIFY_EMAIL");
        var account = lock(challenge.accountId);
        check(account, challenge);
        account.verifyRecoveryEmail(challenge.email);
        challenges.deleteByAccountId(account.getId());
        audit.save(new SecurityAuditEvent(account.getId(), account.getId(), "RECOVERY_EMAIL_VERIFIED", clock.instant()));
    }

    public void reset(String token, String password) {
        if (!validPasswordInput(password) || password.length() < 15) throw new RecoveryFailure();
        var challenge = redeem(token, "RESET_PASSWORD");
        var account = lock(challenge.accountId);
        check(account, challenge);
        if (!challenge.email.equals(account.getRecoveryEmail())) throw new RecoveryFailure();
        account.resetPasswordHash(encoder.encode(password));
        challenges.deleteByAccountId(account.getId());
        audit.save(new SecurityAuditEvent(null, account.getId(), "PASSWORD_RECOVERED_TOKENS_REVOKED", clock.instant()));
        // No role/enabled/employee changes and no auto-login.
    }

    private RecoveryChallenge redeem(String token, String purpose) {
        if (!delivery.available() || token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new RecoveryFailure();
        var initial = challenges.findByTokenHash(hash(token)).orElseThrow(RecoveryFailure::new);
        // All issuance/redemption locks the account first; refresh challenge after waiting for that lock.
        lock(initial.accountId);
        if (!challenges.existsById(initial.id)) throw new RecoveryFailure();
        var challenge = challenges.findById(initial.id).orElseThrow(RecoveryFailure::new);
        if (challenge.consumed || !purpose.equals(challenge.purpose)
                || !clock.instant().isBefore(challenge.expiresAt)) throw new RecoveryFailure();
        challenge.consumed = true;
        return challenge;
    }

    private UserAccount lock(Long id) {
        var account = accounts.lockById(id).orElseThrow(RecoveryFailure::new);
        // A prior username lookup may already have populated the first-level cache before the lock.
        entityManager.refresh(account, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return account;
    }

    private void check(UserAccount account, RecoveryChallenge challenge) {
        if (!AccountAccessPolicy.canAuthenticate(account)
                || account.getCredentialVersion() != challenge.credentialVersion) throw new RecoveryFailure();
    }
    private boolean canSend(UserAccount account) {
        return account.getRecoveryLastSentAt() == null
                || !clock.instant().isBefore(account.getRecoveryLastSentAt().plusSeconds(60));
    }
    private void issue(UserAccount account, String purpose, String email, Long actor) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenges.deleteByAccountId(account.getId());
        challenges.flush();
        challenges.saveAndFlush(new RecoveryChallenge(account.getId(), purpose, hash(token), email,
                account.getCredentialVersion(), clock.instant().plusSeconds(900)));
        account.recordRecoveryDelivery(clock.instant());
        audit.save(new SecurityAuditEvent(actor, account.getId(), purpose + "_REQUESTED", clock.instant()));
        // SMTP failure rolls the transaction back; a delivered message before rollback is unusable.
        delivery.send(email, purpose, token);
    }
    private boolean validPasswordInput(String password) {
        return password != null && !password.isBlank() && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
