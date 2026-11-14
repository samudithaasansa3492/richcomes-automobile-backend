package com.richcomes.garage.audit.service;

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
import java.util.Locale;

@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Records an audit event from a trusted backend service. */
    public AuditLog logEvent(Long actorUserId, String action, String entityType,
                             Long entityId, String oldValue, String newValue,
                             String summary, String ipAddress) {
        requirePositiveId(actorUserId, "Actor user");
        requirePositiveId(entityId, "Entity");
        String normalizedAction = requireText(action, "Action", 80).toUpperCase(Locale.ROOT);
        String normalizedEntityType = requireText(entityType, "Entity type", 80);
        String normalizedSummary = requireText(summary, "Summary", 0);
        if (ipAddress != null && ipAddress.length() > 50) {
            throw new IllegalArgumentException("IP address cannot exceed 50 characters");
        }

        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(normalizedAction);
        log.setEntityType(normalizedEntityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setSummary(normalizedSummary);
        log.setIpAddress(ipAddress);
        log.setTimestamp(LocalDateTime.now());
        return auditLogRepository.save(log);
    }

    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " ID must be positive");
        }
    }

    private String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String clean = value.trim();
        if (maxLength > 0 && clean.length() > maxLength) {
            throw new IllegalArgumentException(name + " cannot exceed " + maxLength + " characters");
        }
        return clean;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAllAuditLogs(
            int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size between 1 and 100");
        }
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
        requirePositiveId(id, "Audit log");
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Audit log ID " + id + " not found"
                ));
        return mapToResponse(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByEntity(String entityType, Long entityId) {
        requirePositiveId(entityId, "Entity");
        String normalizedType = requireText(entityType, "Entity type", 80);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(normalizedType, entityId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByActor(Long actorUserId) {
        requirePositiveId(actorUserId, "Actor user");
        return auditLogRepository.findByActorUserIdOrderByTimestampDesc(actorUserId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByAction(String action) {
        String normalizedAction = requireText(action, "Action", 80).toUpperCase(Locale.ROOT);
        return auditLogRepository.findByActionOrderByTimestampDesc(normalizedAction)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }
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
