
package com.richcomes.garage.inventory.stockmovement.repository;

import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long> {

    // Get transaction history of a stock item
    List<StockTransaction>
    findByStockItemIdOrderByCreatedAtDesc(Long stockItemId);

    // Get all transactions ordered by newest first
    List<StockTransaction> findAllByOrderByCreatedAtDesc();

    // Get transactions by type
    List<StockTransaction>
    findByTransactionTypeOrderByCreatedAtDesc(
            StockTransactionType transactionType
    );

    // Get transactions for an external reference
    List<StockTransaction>
    findByReferenceTypeAndReferenceId(
            String referenceType,
            Long referenceId
    );
}
