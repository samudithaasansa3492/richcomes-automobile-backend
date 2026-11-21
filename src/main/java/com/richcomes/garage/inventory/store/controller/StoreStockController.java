
package com.richcomes.garage.inventory.store.controller;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/stores")
public class StoreStockController {

    private final StoreLocationRepository storeLocationRepository;
    private final StoreStockBalanceRepository balanceRepository;

    public StoreStockController(
            StoreLocationRepository storeLocationRepository,
            StoreStockBalanceRepository balanceRepository) {

        this.storeLocationRepository = storeLocationRepository;
        this.balanceRepository = balanceRepository;
    }

    // Get products and available quantities for selected store.
    @GetMapping("/{storeId}/stock")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StoreStockResponse>> getStoreStock(
            @PathVariable Long storeId) {

        StoreLocation store = storeLocationRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Store location not found"
                ));

        if (!store.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Store location is inactive"
            );
        }

        List<StoreStockResponse> response = balanceRepository
                .findByStoreLocationIdOrderByStockItemIdAsc(storeId)
                .stream()
                .filter(balance -> balance.getStockItem().isActive())
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    private StoreStockResponse toResponse(StoreStockBalance balance) {

        StockItem item = balance.getStockItem();

        return new StoreStockResponse(
                balance.getStoreLocation().getId(),
                item.getId(),
                item.getItemCode(),
                item.getItemName(),
                item.getUnit(),
                balance.getQuantity(),
                item.getSellingPrice()
        );
    }

    // Only fields needed by the store inventory screen.
    public record StoreStockResponse(
            Long storeId,
            Long itemId,
            String itemCode,
            String itemName,
            String unit,
            BigDecimal availableQuantity,
            BigDecimal sellingPrice
    ) {
    }
}
