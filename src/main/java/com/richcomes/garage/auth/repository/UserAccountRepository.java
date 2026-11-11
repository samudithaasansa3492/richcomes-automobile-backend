package com.richcomes.garage.auth.repository;

import com.richcomes.garage.auth.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    // Callers must pass UserAccount.normalizeUsername(input).
    @EntityGraph(attributePaths = {"employee", "roles", "roles.permissions"})
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = {"employee", "roles", "roles.permissions"})
    Optional<UserAccount> findOneById(Long id);

    Optional<UserAccount> findByEmployee_Id(UUID employeeId);

    @Query("""
            select count(a) from UserAccount a join a.roles r
            where a.enabled = true and a.employee.active = true and r.code = 'OWNER_ADMIN'
            """)
    long countActiveOwners();

    boolean existsByUsername(String username);

    boolean existsByEmployee_Id(UUID employeeId);
}
