
package com.richcomes.garage.inventory.store.repository;

import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface StoreStockBalanceRepository
        extends JpaRepository<StoreStockBalance, Long> {

    // Selected store eke stock balances witharai
    List<StoreStockBalance> findByStoreLocationIdOrderByStockItemIdAsc(
            Long storeLocationId
    );

    // Specific product ekaka selected store balance
    Optional<StoreStockBalance> findByStoreLocationIdAndStockItemId(
            Long storeLocationId,
            Long stockItemId
    );

    // Concurrent stock updates walata row lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM StoreStockBalance b
            WHERE b.storeLocation.id = :storeId
              AND b.stockItem.id = :itemId
            """)
    Optional<StoreStockBalance> findForUpdate(
            @Param("storeId") Long storeId,
            @Param("itemId") Long itemId
    );

    // Product ekaka stores okkoma balances
    List<StoreStockBalance> findByStockItemId(
            Long stockItemId
    );
}
