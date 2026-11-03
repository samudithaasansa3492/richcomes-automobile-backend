
package com.richcomes.garage.inventory.grn.repository;

import com.richcomes.garage.inventory.grn.entity.GRN;
import com.richcomes.garage.inventory.grn.enums.GRNStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GRNRepository extends JpaRepository<GRN, Long> {

    Optional<GRN> findByGrnNumber(String grnNumber);

    boolean existsByGrnNumber(String grnNumber);

    List<GRN> findByStatusOrderByCreatedAtDesc(GRNStatus status);

    List<GRN> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

    List<GRN> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GRN g WHERE g.id = :id")
    Optional<GRN> findByIdForUpdate(@Param("id") Long id);
}
