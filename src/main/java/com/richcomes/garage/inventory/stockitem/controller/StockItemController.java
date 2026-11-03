
package com.richcomes.garage.inventory.stockitem.controller;

import com.richcomes.garage.inventory.stockitem.dto.request.StockItemRequest;
import com.richcomes.garage.inventory.stockitem.dto.responce.StockItemResponse;
import com.richcomes.garage.inventory.stockitem.service.StockItemService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/items")
public class StockItemController {

    private final StockItemService service;

    public StockItemController(StockItemService service) {
        this.service = service;
    }

    // CREATE STOCK ITEM
    @PostMapping
    public ResponseEntity<StockItemResponse> createItem(
            @Valid @RequestBody StockItemRequest request) {

        StockItemResponse response = service.createItem(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET ALL STOCK ITEMS
    @GetMapping
    public ResponseEntity<List<StockItemResponse>> getAllItems() {

        return ResponseEntity.ok(service.getAllItems());
    }

    // GET STOCK ITEM BY ID
    @GetMapping("/{id}")
    public ResponseEntity<StockItemResponse> getItemById(
            @PathVariable Long id) {

        return ResponseEntity.ok(service.getItemById(id));
    }

    // GET ITEMS BY CATEGORY
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<StockItemResponse>> getItemsByCategory(
            @PathVariable Long categoryId) {

        return ResponseEntity.ok(
                service.getItemsByCategory(categoryId)
        );
    }

    // GET LOW STOCK ITEMS
    @GetMapping("/low-stock")
    public ResponseEntity<List<StockItemResponse>> getLowStockItems() {

        return ResponseEntity.ok(service.getLowStockItems());
    }

    // UPDATE STOCK ITEM DETAILS
    @PutMapping("/{id}")
    public ResponseEntity<StockItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody StockItemRequest request) {

        return ResponseEntity.ok(service.updateItem(id, request));
    }

    // DEACTIVATE STOCK ITEM
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateItem(
            @PathVariable Long id) {

        service.deactivateItem(id);

        return ResponseEntity.noContent().build();
    }

    // ACTIVATE STOCK ITEM
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateItem(
            @PathVariable Long id) {

        service.activateItem(id);

        return ResponseEntity.noContent().build();
    }
}
