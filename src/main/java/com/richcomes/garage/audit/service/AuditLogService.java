package com.richcomes.garage.audit.service;

import com.richcomes.garage.audit.dto.AuditLogCreateRequest;
import com.richcomes.garage.audit.dto.AuditLogResponse;
import com.richcomes.garage.audit.entity.AuditLog;
import com.richcomes.garage.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records a business or security action in the audit trail.
     * Conforms to PDF Section 13 (Important Business Rules) and Section 14 (Finalize Job Transaction Step 9).
     *//**
     * Programmatic helper for cross-module service invocation.
     */
    public AuditLog logEvent(Long actorUserId, String action, String entityType, Long entityId,
                             String oldValue, String newValue, String summary, String ipAddress) {
        AuditLog log = new AuditLog();
        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Actor user ID is required"
            );
        }

        log.setActorUserId(actorUserId);
        log.setAction(action.trim().toUpperCase());
        log.setEntityType(entityType.trim());
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setSummary(summary.trim());
        log.setIpAddress(ipAddress);
        log.setTimestamp(LocalDateTime.now());
        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAllAuditLogs(
            int page, int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "timestamp",
                        "id"
                )
        );

        return auditLogRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(Long id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Audit log ID " + id + " not found"
                ));
        return mapToResponse(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByActor(Long actorUserId) {
        return auditLogRepository.findByActorUserIdOrderByTimestampDesc(actorUserId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByAction(String action) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action.trim().toUpperCase())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AuditLogResponse mapToResponse(AuditLog log) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(log.getId());
        response.setActorUserId(log.getActorUserId());
        response.setAction(log.getAction());
        response.setEntityType(log.getEntityType());
        response.setEntityId(log.getEntityId());
        response.setOldValue(log.getOldValue());
        response.setNewValue(log.getNewValue());
        response.setSummary(log.getSummary());
        response.setTimestamp(log.getTimestamp());
        response.setIpAddress(log.getIpAddress());
        return response;
    }
}
