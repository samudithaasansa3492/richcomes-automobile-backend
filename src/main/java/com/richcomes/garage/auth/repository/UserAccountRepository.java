package com.richcomes.garage.auth.repository;

import com.richcomes.garage.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    // Callers must pass UserAccount.normalizeUsername(input).
    @EntityGraph(attributePaths = {"employee", "roles", "roles.permissions"})
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = {"employee", "roles", "roles.permissions"})
    Optional<UserAccount> findOneById(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmployee_Id(UUID employeeId);
}
