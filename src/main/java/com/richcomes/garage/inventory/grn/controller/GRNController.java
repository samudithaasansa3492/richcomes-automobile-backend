
package com.richcomes.garage.inventory.grn.controller;

import com.richcomes.garage.inventory.grn.dto.request.GRNRequest;
import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.service.GRNService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // Stock-changing confirm/cancel endpoints
    // will be added after authorization review.
}
