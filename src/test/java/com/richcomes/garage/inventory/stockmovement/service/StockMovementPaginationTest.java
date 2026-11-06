
package com.richcomes.garage.inventory.stockmovement.service;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;

import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockMovementPaginationTest {

    private StockItemRepository itemRepository;
    private StockTransactionRepository transactionRepository;

    private StockMovementService stockMovementService;

    @BeforeEach
    void setUp() {

        itemRepository = mock(StockItemRepository.class);

        transactionRepository =
                mock(StockTransactionRepository.class);

        stockMovementService = new StockMovementService(
                itemRepository,
                transactionRepository
        );
    }

    // CREATE A MOCK TRANSACTION FOR READ-ONLY TESTS
    private StockTransaction createMockTransaction(
            StockTransactionType type) {

        StockItem item = mock(StockItem.class);

        when(item.getId()).thenReturn(10L);
        when(item.getItemCode()).thenReturn("OIL-001");
        when(item.getItemName()).thenReturn("Engine Oil");

        StockTransaction transaction =
                mock(StockTransaction.class);

        when(transaction.getId()).thenReturn(1L);
        when(transaction.getStockItem()).thenReturn(item);
        when(transaction.getTransactionType()).thenReturn(type);
        when(transaction.getQuantity()).thenReturn(2);
        when(transaction.getQuantityBefore()).thenReturn(15);
        when(transaction.getQuantityAfter()).thenReturn(13);

        return transaction;
    }

    // TEST 1: RECENT TRANSACTIONS USE DATABASE PAGINATION
    @Test
    void shouldGetOnlyRecentTransactionsUsingPagination() {

        StockTransaction transaction =
                createMockTransaction(StockTransactionType.DAMAGE);

        when(transactionRepository
                .findAllByOrderByCreatedAtDesc(
                        any(Pageable.class)
                ))
                .thenReturn(List.of(transaction));

        List<StockMovementResponse> result =
                stockMovementService.getRecentTransactions();

        assertEquals(1, result.size());

        assertEquals("OIL-001", result.get(0).getItemCode());

        assertEquals(
                StockTransactionType.DAMAGE,
                result.get(0).getTransactionType()
        );

        assertEquals(2, result.get(0).getQuantity());

        // Verify database query receives page 0, size 20
        verify(transactionRepository)
                .findAllByOrderByCreatedAtDesc(
                        PageRequest.of(0, 20)
                );

        // Full-history query must not run
        verify(transactionRepository, never())
                .findAllByOrderByCreatedAtDesc();

        // No database writes
        verify(transactionRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(itemRepository, never()).save(any());
        verify(itemRepository, never()).saveAndFlush(any());
    }

    // TEST 2: DAMAGE FILTER USES DATABASE PAGINATION
    @Test
    void shouldFilterDamageTransactionsUsingPagination() {

        StockTransaction transaction =
                createMockTransaction(StockTransactionType.DAMAGE);

        when(transactionRepository
                .findByTransactionTypeOrderByCreatedAtDesc(
                        eq(StockTransactionType.DAMAGE),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(transaction));

        List<StockMovementResponse> result =
                stockMovementService.getTransactionsByType(
                        StockTransactionType.DAMAGE
                );

        assertEquals(1, result.size());

        assertEquals("OIL-001", result.get(0).getItemCode());

        assertEquals(
                StockTransactionType.DAMAGE,
                result.get(0).getTransactionType()
        );

        // Verify filtering and pagination together
        verify(transactionRepository)
                .findByTransactionTypeOrderByCreatedAtDesc(
                        StockTransactionType.DAMAGE,
                        PageRequest.of(0, 20)
                );

        // Old unlimited query must not run
        verify(transactionRepository, never())
                .findByTransactionTypeOrderByCreatedAtDesc(
                        StockTransactionType.DAMAGE
                );

        // No database writes
        verify(transactionRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(itemRepository, never()).save(any());
        verify(itemRepository, never()).saveAndFlush(any());
    }
}
