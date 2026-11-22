package com.richcomes.garage.audit.service;

import com.richcomes.garage.audit.dto.AuditLogResponse;
import com.richcomes.garage.audit.entity.AuditLog;
import com.richcomes.garage.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = createTestAuditLog();
    }

    private AuditLog createTestAuditLog() {
        AuditLog log = new AuditLog();
        log.setActorUserId(5L);
        log.setAction("UPDATE");
        log.setEntityType("SERVICE_HISTORY");
        log.setEntityId(100L);
        log.setOldValue("odometer=50000, summary=Old");
        log.setNewValue("odometer=55000, summary=New");
        log.setSummary("Service history updated: corrected odometer");
        log.setIpAddress("192.168.1.1");
        log.setTimestamp(LocalDateTime.of(2024, 1, 15, 10, 30));
        try {
            java.lang.reflect.Field idField = AuditLog.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(log, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return log;
    }

    // =====================================================
    // LOG EVENT TESTS
    // =====================================================

    @Test
    void logEvent_withValidParams_createsAndReturnsAuditLog() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleLog);

        AuditLog log = auditLogService.logEvent(
                5L, "UPDATE", "SERVICE_HISTORY", 100L,
                "odometer=50000", "odometer=55000",
                "Service history updated: corrected odometer", "192.168.1.1");

        assertNotNull(log);
        assertEquals(1L, log.getId());
        assertEquals(5L, log.getActorUserId());
        assertEquals("UPDATE", log.getAction());
        assertEquals("SERVICE_HISTORY", log.getEntityType());
        assertEquals(100L, log.getEntityId());
        assertEquals("192.168.1.1", log.getIpAddress());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void logEvent_withNullActorId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(null, "UPDATE", "ENTITY", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Actor user ID must be positive"));
    }

    @Test
    void logEvent_withZeroActorId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(0L, "UPDATE", "ENTITY", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Actor user ID must be positive"));
    }

    @Test
    void logEvent_withNegativeActorId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(-1L, "UPDATE", "ENTITY", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Actor user ID must be positive"));
    }

    @Test
    void logEvent_withNullEntityId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", "ENTITY", null,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Entity ID must be positive"));
    }

    @Test
    void logEvent_withZeroEntityId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", "ENTITY", 0L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Entity ID must be positive"));
    }

    @Test
    void logEvent_withNullAction_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, null, "ENTITY", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Action is required"));
    }

    @Test
    void logEvent_withBlankAction_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "   ", "ENTITY", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Action is required"));
    }

    @Test
    void logEvent_withActionExceedingMaxLength_throwsIllegalArgumentException() {
        String longAction = "A".repeat(81);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, longAction, "ENTITY", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("cannot exceed 80 characters"));
    }

    @Test
    void logEvent_normalizesActionToUpperCase() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleLog);

        auditLogService.logEvent(1L, "update", "entity", 1L, "old", "new", "summary", "192.168.1.1");

        verify(auditLogRepository).save(argThat(log -> "UPDATE".equals(log.getAction())));
    }

    @Test
    void logEvent_withNullEntityType_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", null, 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Entity type is required"));
    }

    @Test
    void logEvent_withBlankEntityType_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", "   ", 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Entity type is required"));
    }

    @Test
    void logEvent_withEntityTypeExceedingMaxLength_throwsIllegalArgumentException() {
        String longType = "A".repeat(81);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", longType, 1L,
                        "old", "new", "summary", "192.168.1.1"));

        assertTrue(ex.getMessage().contains("cannot exceed 80 characters"));
    }

    @Test
    void logEvent_withNullSummary_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", "ENTITY", 1L,
                        "old", "new", null, "192.168.1.1"));

        assertTrue(ex.getMessage().contains("Summary is required"));
    }

    @Test
    void logEvent_withIpAddressExceedingMaxLength_throwsIllegalArgumentException() {
        String longIp = "1".repeat(51);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.logEvent(1L, "UPDATE", "ENTITY", 1L,
                        "old", "new", "summary", longIp));

        assertTrue(ex.getMessage().contains("IP address cannot exceed 50 characters"));
    }

    @Test
    void logEvent_withNullIpAddress_allowsNull() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleLog);

        AuditLog log = auditLogService.logEvent(1L, "UPDATE", "ENTITY", 1L,
                "old", "new", "summary", null);

        assertNotNull(log);
        verify(auditLogRepository).save(argThat(l -> l.getIpAddress() == null));
    }

    @Test
    void logEvent_setsTimestampAutomatically() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleLog);

        auditLogService.logEvent(1L, "UPDATE", "ENTITY", 1L, "old", "new", "summary", "192.168.1.1");

        verify(auditLogRepository).save(argThat(log -> log.getTimestamp() != null));
    }

    // =====================================================
    // GET ALL AUDIT LOGS TESTS
    // =====================================================

    @Test
    void getAllAuditLogs_withValidPage_returnsPage() {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLog));
        when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<AuditLogResponse> response = auditLogService.getAllAuditLogs(0, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void getAllAuditLogs_withNegativePage_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAllAuditLogs(-1, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAllAuditLogs_withZeroSize_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAllAuditLogs(0, 0));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAllAuditLogs_withSizeOver100_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAllAuditLogs(0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // =====================================================
    // GET AUDIT LOG BY ID TESTS
    // =====================================================

    @Test
    void getAuditLogById_withExistingId_returnsLog() {
        when(auditLogRepository.findById(1L)).thenReturn(Optional.of(sampleLog));

        AuditLogResponse response = auditLogService.getAuditLogById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(5L, response.getActorUserId());
    }

    @Test
    void getAuditLogById_withNonExistentId_throwsNotFound() {
        when(auditLogRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAuditLogById(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not found"));
    }

    @Test
    void getAuditLogById_withInvalidId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.getAuditLogById(0L));

        assertTrue(ex.getMessage().contains("Audit log ID must be positive"));
    }

    // =====================================================
    // GET AUDIT LOGS BY ENTITY TESTS
    // =====================================================

    @Test
    void getAuditLogsByEntity_withValidParams_returnsList() {
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("SERVICE_HISTORY", 100L))
                .thenReturn(List.of(sampleLog));

        List<AuditLogResponse> response = auditLogService.getAuditLogsByEntity("SERVICE_HISTORY", 100L);

        assertEquals(1, response.size());
        assertEquals("SERVICE_HISTORY", response.getFirst().getEntityType());
    }

    @Test
    void getAuditLogsByEntity_withNullEntityType_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.getAuditLogsByEntity(null, 100L));

        assertTrue(ex.getMessage().contains("Entity type is required"));
    }

    @Test
    void getAuditLogsByEntity_withBlankEntityType_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.getAuditLogsByEntity("   ", 100L));

        assertTrue(ex.getMessage().contains("Entity type is required"));
    }

    @Test
    void getAuditLogsByEntity_withInvalidEntityId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.getAuditLogsByEntity("SERVICE_HISTORY", -1L));

        assertTrue(ex.getMessage().contains("Entity ID must be positive"));
    }

    // =====================================================
    // GET AUDIT LOGS BY ACTOR TESTS
    // =====================================================

    @Test
    void getAuditLogsByActor_withValidActorId_returnsList() {
        when(auditLogRepository.findByActorUserIdOrderByTimestampDesc(5L))
                .thenReturn(List.of(sampleLog));

        List<AuditLogResponse> response = auditLogService.getAuditLogsByActor(5L);

        assertEquals(1, response.size());
        assertEquals(5L, response.getFirst().getActorUserId());
    }

    @Test
    void getAuditLogsByActor_withInvalidActorId_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.getAuditLogsByActor(0L));

        assertTrue(ex.getMessage().contains("Actor user ID must be positive"));
    }

    // =====================================================
    // GET AUDIT LOGS BY ACTION TESTS
    // =====================================================

    @Test
    void getAuditLogsByAction_withValidAction_returnsList() {
        when(auditLogRepository.findByActionOrderByTimestampDesc("UPDATE"))
                .thenReturn(List.of(sampleLog));

        List<AuditLogResponse> response = auditLogService.getAuditLogsByAction("UPDATE");

        assertEquals(1, response.size());
        assertEquals("UPDATE", response.getFirst().getAction());
    }

    @Test
    void getAuditLogsByAction_withNullAction_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditLogService.getAuditLogsByAction(null));

        assertTrue(ex.getMessage().contains("Action is required"));
    }

    @Test
    void getAuditLogsByAction_normalizesActionToUpperCase() {
        when(auditLogRepository.findByActionOrderByTimestampDesc("UPDATE"))
                .thenReturn(List.of(sampleLog));

        auditLogService.getAuditLogsByAction("update");

        verify(auditLogRepository).findByActionOrderByTimestampDesc("UPDATE");
    }

    // =====================================================
    // GET AUDIT LOGS BY DATE RANGE TESTS
    // =====================================================

    @Test
    void getAuditLogsByDateRange_withValidRange_returnsList() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
        when(auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end))
                .thenReturn(List.of(sampleLog));

        List<AuditLogResponse> response = auditLogService.getAuditLogsByDateRange(start, end);

        assertEquals(1, response.size());
    }

    @Test
    void getAuditLogsByDateRange_withNullStart_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAuditLogsByDateRange(null, LocalDateTime.now()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAuditLogsByDateRange_withNullEnd_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAuditLogsByDateRange(LocalDateTime.now(), null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAuditLogsByDateRange_withStartAfterEnd_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auditLogService.getAuditLogsByDateRange(
                        LocalDateTime.of(2024, 12, 31, 23, 59),
                        LocalDateTime.of(2024, 1, 1, 0, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // =====================================================
    // MAP TO RESPONSE TESTS
    // =====================================================

    @Test
    void mapToResponse_mapsAllFields() {
        AuditLogResponse response = auditLogService.mapToResponse(sampleLog);

        assertEquals(1L, response.getId());
        assertEquals(5L, response.getActorUserId());
        assertEquals("UPDATE", response.getAction());
        assertEquals("SERVICE_HISTORY", response.getEntityType());
        assertEquals(100L, response.getEntityId());
        assertEquals("odometer=50000, summary=Old", response.getOldValue());
        assertEquals("odometer=55000, summary=New", response.getNewValue());
        assertEquals("Service history updated: corrected odometer", response.getSummary());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), response.getTimestamp());
        assertEquals("192.168.1.1", response.getIpAddress());
    }

    @Test
    void mapToResponse_withNullOldNewValues_mapsCorrectly() {
        AuditLog log = new AuditLog();
        log.setActorUserId(1L);
        log.setAction("CREATE");
        log.setEntityType("NOTIFICATION");
        log.setEntityId(50L);
        log.setOldValue(null);
        log.setNewValue(null);
        log.setSummary("Notification created");
        log.setIpAddress(null);
        log.setTimestamp(LocalDateTime.now());
        try {
            java.lang.reflect.Field idField = AuditLog.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(log, 2L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AuditLogResponse response = auditLogService.mapToResponse(log);

        assertNull(response.getOldValue());
        assertNull(response.getNewValue());
        assertNull(response.getIpAddress());
    }
}