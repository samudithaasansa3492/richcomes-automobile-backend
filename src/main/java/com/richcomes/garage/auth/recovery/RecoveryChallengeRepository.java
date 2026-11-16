package com.richcomes.garage.auth.recovery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface RecoveryChallengeRepository extends JpaRepository<RecoveryChallenge, UUID> {
    Optional<RecoveryChallenge> findByTokenHash(String hash);
    void deleteByAccountId(Long accountId);
}
