package com.richcomes.garage.auth.repository;

import com.richcomes.garage.auth.entity.Role;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.code = 'OWNER_ADMIN'")
    Optional<Role> lockOwnerRole();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCode(String code);
}
