
package com.richcomes.garage.history.controller;

import com.richcomes.garage.history.dto.ServiceHistoryCreateRequest;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.dto.ServiceHistoryUpdateRequest;
import com.richcomes.garage.history.service.ServiceHistoryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/history")
public class ServiceHistoryController {

    private final ServiceHistoryService serviceHistoryService;

    public ServiceHistoryController(
            ServiceHistoryService serviceHistoryService) {

        this.serviceHistoryService = serviceHistoryService;
    }

    // =====================================================
    // CREATE SERVICE HISTORY
    // =====================================================

    @PostMapping
    public ResponseEntity<ServiceHistoryResponse> createServiceHistory(
            @Valid @RequestBody ServiceHistoryCreateRequest request) {

        ServiceHistoryResponse response =
                serviceHistoryService.recordServiceHistory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =====================================================
    // GET ALL / FILTER BY DATE / PAGINATION
    // =====================================================

    @GetMapping
    public ResponseEntity<Page<ServiceHistoryResponse>> getAllHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().build();
        }

        if ((startDate == null) != (endDate == null)) {
            return ResponseEntity.badRequest().build();
        }

        if (startDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }

        if (startDate != null) {
            return ResponseEntity.ok(
                    serviceHistoryService.getServiceHistoryByDateRange(
                            startDate, endDate, page, size
                    )
            );
        }

        return ResponseEntity.ok(
                serviceHistoryService.getAllServiceHistories(page, size)
        );
    }

    // =====================================================
    // TEST ENDPOINT
    // =====================================================

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Service History API Working!");
    }

    // =====================================================
    // GET BY HISTORY ID
    // =====================================================

    @GetMapping("/{id}")
    public ResponseEntity<ServiceHistoryResponse> getHistoryById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                serviceHistoryService.getServiceHistoryById(id)
        );
    }

    // =====================================================
    // GET BY VEHICLE ID
    // =====================================================

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<ServiceHistoryResponse>> getHistoryByVehicle(
            @PathVariable Long vehicleId) {

        return ResponseEntity.ok(
                serviceHistoryService.getServiceHistoryByVehicleId(vehicleId)
        );
    }

    // =====================================================
    // GET BY JOB ID
    // =====================================================

    @GetMapping("/job/{jobId}")
    public ResponseEntity<ServiceHistoryResponse> getHistoryByJob(
            @PathVariable Long jobId) {

        return ResponseEntity.ok(
                serviceHistoryService.getServiceHistoryByJobId(jobId)
        );
    }

    // =====================================================
    // GET BY TECHNICIAN ID
    // =====================================================

    @GetMapping("/technician/{technicianId}")
    public ResponseEntity<List<ServiceHistoryResponse>> getHistoryByTechnician(
            @PathVariable Long technicianId) {

        return ResponseEntity.ok(
                serviceHistoryService.getServiceHistoryByTechnician(
                        technicianId
                )
        );
    }

    // =====================================================
    // UPDATE HISTORY + AUDIT LOG
    // =====================================================

    @PatchMapping("/{id}")
    public ResponseEntity<ServiceHistoryResponse> updateHistory(
            @PathVariable Long id,
            @Valid @RequestBody ServiceHistoryUpdateRequest request,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {

        Long actorUserId = getAuthenticatedUserId();

        String ipAddress = httpRequest.getRemoteAddr();

        ServiceHistoryResponse response =
                serviceHistoryService.updateServiceHistory(
                        id,
                        request,
                        actorUserId,
                        reason,
                        ipAddress
                );

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // DELETE HISTORY - UNCHANGED
    // =====================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistory(
            @PathVariable Long id) {

        serviceHistoryService.deleteServiceHistory(id);

        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // AUTHENTICATED USER ID HELPER
    // =====================================================

    private Long getAuthenticatedUserId() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required to update history"
            );
        }

        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not available"
            );
        }
    }
}
