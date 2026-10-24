
package com.richcomes.garage.inventory.controller;

import com.richcomes.garage.inventory.dto.request.StockCategoryRequest;
import com.richcomes.garage.inventory.dto.response.StockCategoryResponse;
import com.richcomes.garage.inventory.service.StockCategoryService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/categories")
public class StockCategoryController {

    private final StockCategoryService service;

    public StockCategoryController(StockCategoryService service) {
        this.service = service;
    }

    // CREATE CATEGORY
    @PostMapping
    public ResponseEntity<StockCategoryResponse> createCategory(
            @Valid @RequestBody StockCategoryRequest request) {

        StockCategoryResponse response =
                service.createCategory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET ALL CATEGORIES
    @GetMapping
    public ResponseEntity<List<StockCategoryResponse>> getAllCategories() {

        return ResponseEntity.ok(service.getAllCategories());
    }

    // GET CATEGORY BY ID
    @GetMapping("/{id}")
    public ResponseEntity<StockCategoryResponse> getCategoryById(
            @PathVariable Long id) {

        return ResponseEntity.ok(service.getCategoryById(id));
    }

    // UPDATE CATEGORY
    @PutMapping("/{id}")
    public ResponseEntity<StockCategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody StockCategoryRequest request) {

        return ResponseEntity.ok(service.updateCategory(id, request));
    }

    // DEACTIVATE CATEGORY
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCategory(
            @PathVariable Long id) {

        service.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ACTIVATE CATEGORY
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateCategory(
            @PathVariable Long id) {

        service.activateCategory(id);
        return ResponseEntity.noContent().build();
    }
}
