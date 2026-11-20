
package com.richcomes.garage.inventory.store.controller;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/stores")
public class StoreLocationController {

    private final StoreLocationRepository storeLocationRepository;

    public StoreLocationController(
            StoreLocationRepository storeLocationRepository) {
        this.storeLocationRepository = storeLocationRepository;
    }

    // Active stores for the frontend location dropdown
    @GetMapping
    public ResponseEntity<List<StoreLocationResponse>> getActiveStores() {

        List<StoreLocationResponse> stores =
                storeLocationRepository
                        .findByActiveTrueOrderByStoreNameAsc()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(stores);
    }

    private StoreLocationResponse toResponse(StoreLocation store) {
        return new StoreLocationResponse(
                store.getId(),
                store.getStoreCode(),
                store.getStoreName()
        );
    }

    // Response DTO: only expose fields needed by the dropdown
    public record StoreLocationResponse(
            Long id,
            String storeCode,
            String storeName
    ) {
    }
}
