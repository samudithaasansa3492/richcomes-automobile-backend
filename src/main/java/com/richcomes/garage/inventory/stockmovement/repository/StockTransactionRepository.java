
package com.richcomes.garage.inventory.stockmovement.repository;

import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long> {

    // EXISTING: Transaction history for one stock item
    List<StockTransaction>
    findByStockItemIdOrderByCreatedAtDesc(Long stockItemId);

    // EXISTING: All transactions, newest first
    List<StockTransaction> findAllByOrderByCreatedAtDesc();

    // EXISTING: Transactions filtered by type
    List<StockTransaction>
    findByTransactionTypeOrderByCreatedAtDesc(
            StockTransactionType transactionType
    );

    // EXISTING: Transactions linked to an external reference
    List<StockTransaction>
    findByReferenceTypeAndReferenceId(
            String referenceType,
            Long referenceId
    );

    // NEW: Latest transactions with database-level limit
    List<StockTransaction>
    findAllByOrderByCreatedAtDesc(Pageable pageable);

    // NEW: Filter by type with database-level limit
    List<StockTransaction>
    findByTransactionTypeOrderByCreatedAtDesc(
            StockTransactionType transactionType,
            Pageable pageable
    );
}
