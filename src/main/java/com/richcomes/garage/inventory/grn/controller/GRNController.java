
package com.richcomes.garage.inventory.grn.controller;

import com.richcomes.garage.inventory.grn.dto.request.GRNRequest;
import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.service.GRNService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/grns")
public class GRNController {

    private final GRNService grnService;

    public GRNController(GRNService grnService) {
        this.grnService = grnService;
    }

    // CREATE DRAFT GRN
    @PostMapping
    public ResponseEntity<GRNResponse> createDraft(
            @Valid @RequestBody GRNRequest request) {

        GRNResponse response = grnService.createDraft(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET ALL GRNS
    @GetMapping
    public ResponseEntity<List<GRNResponse>> getAllGRNs() {

        return ResponseEntity.ok(grnService.getAllGRNs());
    }

    // GET GRN BY ID
    @GetMapping("/{id}")
    public ResponseEntity<GRNResponse> getGRNById(
            @PathVariable Long id) {

        return ResponseEntity.ok(grnService.getGRNById(id));
    }

    // CONFIRM DRAFT GRN
    // Stock increases only when GRNService confirms.
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<GRNResponse> confirmGRN(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                grnService.confirmGRN(id)
        );
    }

    // CANCEL DRAFT GRN
    // Only DRAFT GRNs can be cancelled.
    // Confirmed GRNs cannot be cancelled using this endpoint.
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<GRNResponse> cancelGRN(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                grnService.cancelGRN(id)
        );
    }
}
