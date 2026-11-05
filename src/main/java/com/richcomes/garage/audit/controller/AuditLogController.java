package com.richcomes.garage.audit.controller;

import com.richcomes.garage.audit.dto.AuditLogCreateRequest;
import com.richcomes.garage.audit.dto.AuditLogResponse;
import com.richcomes.garage.audit.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // Audit entries must be created internally by trusted services.
    // Remove the public POST endpoint.

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                auditLogService.getAllAuditLogs(page, size)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getAuditLogById(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogService.getAuditLogById(id));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByEntity(entityType, entityId));
    }
    @GetMapping("/date-range")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByDateRange(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        if (start.isAfter(end)) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                auditLogService.getAuditLogsByDateRange(start, end)
        );
    }

    @GetMapping("/actor/{actorUserId}")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByActor(@PathVariable Long actorUserId) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByActor(actorUserId));
    }

    @GetMapping("/action/{action}")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByAction(@PathVariable String action) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByAction(action));
    }
}
