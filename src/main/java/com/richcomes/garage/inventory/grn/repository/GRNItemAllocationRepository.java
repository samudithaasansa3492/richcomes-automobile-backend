
package com.richcomes.garage.inventory.grn.repository;

import com.richcomes.garage.inventory.grn.entity.GRNItemAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GRNItemAllocationRepository
        extends JpaRepository<GRNItemAllocation, Long> {

    // One GRN item ekata adala store allocations
    List<GRNItemAllocation> findByGrnItemIdOrderByStoreLocationIdAsc(
            Long grnItemId
    );

    // One GRN item + one store allocation
    Optional<GRNItemAllocation> findByGrnItemIdAndStoreLocationId(
            Long grnItemId,
            Long storeLocationId
    );

    // GRN item ekaka allocations exist wenawada?
    boolean existsByGrnItemId(Long grnItemId);

    // Entire GRN ekata adala allocations
    List<GRNItemAllocation> findByGrnItemGrnId(
            Long grnId
    );
}
