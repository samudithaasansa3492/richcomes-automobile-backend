
package com.richcomes.garage.inventory.controller;

import com.richcomes.garage.inventory.dto.request.SupplierRequest;
import com.richcomes.garage.inventory.dto.response.SupplierResponse;
import com.richcomes.garage.inventory.service.SupplierService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    // CREATE SUPPLIER
    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @Valid @RequestBody SupplierRequest request) {

        SupplierResponse response =
                service.createSupplier(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET ALL SUPPLIERS
    @GetMapping
    public ResponseEntity<List<SupplierResponse>> getAllSuppliers() {

        return ResponseEntity.ok(service.getAllSuppliers());
    }

    // GET SUPPLIER BY ID
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(
            @PathVariable Long id) {

        return ResponseEntity.ok(service.getSupplierById(id));
    }

    // UPDATE SUPPLIER
    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {

        return ResponseEntity.ok(
                service.updateSupplier(id, request)
        );
    }

    // DEACTIVATE SUPPLIER
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateSupplier(
            @PathVariable Long id) {

        service.deactivateSupplier(id);

        return ResponseEntity.noContent().build();
    }

    // ACTIVATE SUPPLIER
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateSupplier(
            @PathVariable Long id) {

        service.activateSupplier(id);

        return ResponseEntity.noContent().build();
    }
}
