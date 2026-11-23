package com.richcomes.garage.inventory.reconciliation.service;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockReconciliationServiceTest {
    @Mock StockItemRepository stockItemRepository;
    @Mock StoreStockBalanceRepository balanceRepository;
    @InjectMocks StockReconciliationService service;

    private StockItem item(String quantity) {
        StockItem item = new StockItem();
        item.setId(1L);
        item.setItemCode("FILTER-1");
        item.setItemName("Oil Filter");
        item.setUnit("PCS");
        item.setCurrentQuantity(new BigDecimal(quantity));
        return item;
    }
    private StoreStockBalance balance(StockItem item, String quantity) {
        StoreStockBalance b = new StoreStockBalance();
        b.setStockItem(item);
        b.setQuantity(new BigDecimal(quantity));
        return b;
    }

    @Test void matchedGlobalAndStores() {
        StockItem item = item("30.000");
        when(stockItemRepository.findAll()).thenReturn(List.of(item));
        when(balanceRepository.findAll()).thenReturn(List.of(balance(item,"10"),balance(item,"15"),balance(item,"5")));
        var report = service.report(false);
        assertEquals("MATCH", report.getFirst().status());
        assertEquals(0, report.getFirst().difference().compareTo(BigDecimal.ZERO));
        assertEquals(3, report.getFirst().storeBalanceCount());
    }

    @Test void mismatchDetectedWithoutWriting() {
        StockItem item = item("30");
        when(stockItemRepository.findAll()).thenReturn(List.of(item));
        when(balanceRepository.findAll()).thenReturn(List.of(balance(item,"10"),balance(item,"15")));
        var report = service.report(true);
        assertEquals("MISMATCH", report.getFirst().status());
        assertEquals(0, report.getFirst().difference().compareTo(new BigDecimal("5")));
        verify(stockItemRepository, never()).save(any());
        verify(balanceRepository, never()).save(any());
    }

    @Test void zeroInventoryWithoutStoreRowsIsValid() {
        when(stockItemRepository.findAll()).thenReturn(List.of(item("0")));
        when(balanceRepository.findAll()).thenReturn(List.of());
        assertEquals("MATCH", service.report(false).getFirst().status());
    }

    @Test void nonzeroInventoryWithoutStoreRowsIsMismatch() {
        when(stockItemRepository.findAll()).thenReturn(List.of(item("10")));
        when(balanceRepository.findAll()).thenReturn(List.of());
        assertEquals("MISMATCH", service.report(false).getFirst().status());
    }

    @Test void negativeStoreBalanceIsInvalid() {
        StockItem item = item("10");
        when(stockItemRepository.findAll()).thenReturn(List.of(item));
        when(balanceRepository.findAll()).thenReturn(List.of(balance(item,"-1")));
        assertEquals("INVALID_BALANCE", service.report(true).getFirst().status());
    }
}
