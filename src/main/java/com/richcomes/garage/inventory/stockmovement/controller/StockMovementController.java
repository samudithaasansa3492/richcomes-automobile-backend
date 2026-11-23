
package com.richcomes.garage.inventory.stockmovement.controller;

import com.richcomes.garage.inventory.stockmovement.dto.request.StockMovementRequest;
import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.service.StockMovementService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/stock-transactions")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(
            StockMovementService stockMovementService) {

        this.stockMovementService = stockMovementService;
    }

    // GET ALL TRANSACTION HISTORY
    @GetMapping
    public List<StockMovementResponse> getAllTransactions() {

        return stockMovementService.getAllTransactions();
    }

    // GET TRANSACTION HISTORY FOR ONE ITEM
    @GetMapping("/item/{itemId}")
    public List<StockMovementResponse> getItemTransactions(
            @PathVariable Long itemId) {

        return stockMovementService.getItemTransactions(itemId);
    }

    // GET ALL TRANSACTIONS FOR ONE STORE.
    @GetMapping("/store/{storeId}")
    public List<StockMovementResponse> getStoreTransactions(@PathVariable Long storeId) {
        return stockMovementService.getStoreTransactions(storeId);
    }

    // GET ONE ITEM'S TRANSACTIONS IN ONE STORE.
    @GetMapping("/item/{itemId}/store/{storeId}")
    public List<StockMovementResponse> getItemStoreTransactions(
            @PathVariable Long itemId, @PathVariable Long storeId) {
        return stockMovementService.getItemStoreTransactions(itemId, storeId);
    }

    // RECORD CONTROLLED STOCK MOVEMENT
    // GRN and Job Card stock movements must use their
    // own trusted backend service flows, not this endpoint.
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<StockMovementResponse> recordMovement(
            @Valid @RequestBody StockMovementRequest request) {

        // Direct stock receipts and job issues should not
        // bypass the GRN / Job Card business workflows.
        if (request.getTransactionType()
                == StockTransactionType.STOCK_IN
                || request.getTransactionType()
                == StockTransactionType.STOCK_OUT) {

            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Direct STOCK_IN/STOCK_OUT is not permitted. Use GRN or Job Card workflows."
            );
        }

        StockMovementResponse response =
                stockMovementService.recordMovement(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
