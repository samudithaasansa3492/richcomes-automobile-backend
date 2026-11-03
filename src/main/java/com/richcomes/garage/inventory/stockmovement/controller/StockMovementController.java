
package com.richcomes.garage.inventory.stockmovement.controller;

import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.service.StockMovementService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/stock-transactions")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(
            StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    @GetMapping
    public List<StockMovementResponse> getAllTransactions() {
        return stockMovementService.getAllTransactions();
    }

    @GetMapping("/item/{itemId}")
    public List<StockMovementResponse> getItemTransactions(
            @PathVariable Long itemId) {
        return stockMovementService.getItemTransactions(itemId);
    }
}
