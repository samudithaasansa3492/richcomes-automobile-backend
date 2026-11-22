package com.richcomes.garage.history.service;

import com.richcomes.garage.audit.service.AuditLogService;
import com.richcomes.garage.history.dto.ServiceHistoryCreateRequest;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.dto.ServiceHistoryUpdateRequest;
import com.richcomes.garage.history.entity.ServiceHistory;
import com.richcomes.garage.history.repository.ServiceHistoryRepository;
import com.richcomes.garage.reminder.service.ServiceReminderService;
import com.richcomes.garage.reminder.dto.GenerateReminderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceHistoryServiceTest {

    @Mock
    private ServiceHistoryRepository serviceHistoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ServiceReminderService serviceReminderService;

    @Mock
    private VehicleCustomerResolver vehicleCustomerResolver;

    @InjectMocks
    private ServiceHistoryService serviceHistoryService;

    private ServiceHistoryCreateRequest validCreateRequest;
    private ServiceHistory savedHistory;

    @BeforeEach
    void setUp() {
        validCreateRequest = new ServiceHistoryCreateRequest();
        validCreateRequest.setJobId(100L);
        validCreateRequest.setVehicleId(10L);
        validCreateRequest.setServiceDate(LocalDate.of(2024, 1, 15));
        validCreateRequest.setOdometer(50000);
        validCreateRequest.setSummary("Regular service - oil change, filter replacement");
        validCreateRequest.setTechnicianId(5L);
        validCreateRequest.setInvoiceId(200L);
        validCreateRequest.setTotalCost(new BigDecimal("15000.00"));
        validCreateRequest.setNextServiceDate(LocalDate.of(2024, 7, 15));

        savedHistory = createTestHistory();
    }

    private ServiceHistory createTestHistory() {
        ServiceHistory history = new ServiceHistory();
        history.setVehicleId(10L);
        history.setJobId(100L);
        history.setServiceDate(LocalDate.of(2024, 1, 15));
        history.setOdometer(50000);
        history.setSummary("Regular service - oil change, filter replacement");
        history.setTechnicianId(5L);
        history.setInvoiceId(200L);
        history.setTotalCost(new BigDecimal("15000.00"));
        history.setNextServiceDate(LocalDate.of(2024, 7, 15));
        // id, createdAt, updatedAt are set by JPA @PrePersist
        // Use reflection to set ID for testing
        try {
            java.lang.reflect.Field idField = ServiceHistory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(history, 1L);
            java.lang.reflect.Field createdAtField = ServiceHistory.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(history, LocalDateTime.now());
            java.lang.reflect.Field updatedAtField = ServiceHistory.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(history, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return history;
    }

    // =====================================================
    // CREATE SERVICE HISTORY TESTS
    // =====================================================

    @Test
    void recordServiceHistory_withValidRequest_createsAndReturnsHistory() {
        when(serviceHistoryRepository.existsByJobId(100L)).thenReturn(false);
        when(serviceHistoryRepository.save(any(ServiceHistory.class))).thenReturn(savedHistory);
        // Next service date far enough in future to allow reminder (>= 7 days from now)
        lenient().when(vehicleCustomerResolver.resolveCustomerId(10L)).thenReturn(1L);

        ServiceHistoryResponse response = serviceHistoryService.recordServiceHistory(validCreateRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(100L, response.getJobId());
        assertEquals(10L, response.getVehicleId());
        assertEquals(new BigDecimal("15000.00"), response.getTotalCost());
        verify(serviceHistoryRepository).save(any(ServiceHistory.class));
    }

    @Test
    void recordServiceHistory_withNullRequest_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Request body is required", ex.getReason());
        verify(serviceHistoryRepository, never()).save(any());
    }

    @Test
    void recordServiceHistory_withNullJobId_throwsBadRequest() {
        validCreateRequest.setJobId(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Job ID must be positive"));
    }

    @Test
    void recordServiceHistory_withNullVehicleId_throwsBadRequest() {
        validCreateRequest.setVehicleId(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Vehicle ID must be positive"));
    }

    @Test
    void recordServiceHistory_withNullServiceDate_throwsBadRequest() {
        validCreateRequest.setServiceDate(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Service date is required", ex.getReason());
    }

    @Test
    void recordServiceHistory_withBlankSummary_throwsBadRequest() {
        validCreateRequest.setSummary("   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Service summary is required", ex.getReason());
    }

    @Test
    void recordServiceHistory_withDuplicateJobId_throwsConflict() {
        when(serviceHistoryRepository.existsByJobId(100L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(validCreateRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists"));
        verify(serviceHistoryRepository, never()).save(any());
    }

    @Test
    void recordServiceHistory_withNextServiceDateBeforeServiceDate_throwsBadRequest() {
        validCreateRequest.setNextServiceDate(LocalDate.of(2024, 1, 10));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Next service date cannot be earlier"));
    }

    @Test
    void recordServiceHistory_withNextServiceDateAllowingReminder_generatesReminder() {
        // Use a date far in the future to ensure reminder is generated
        ServiceHistoryCreateRequest request = new ServiceHistoryCreateRequest();
        request.setJobId(200L);
        request.setVehicleId(20L);
        request.setServiceDate(LocalDate.of(2024, 1, 15));
        request.setOdometer(50000);
        request.setSummary("Regular service");
        request.setTechnicianId(5L);
        request.setInvoiceId(200L);
        request.setTotalCost(new BigDecimal("15000.00"));
        request.setNextServiceDate(LocalDate.now().plusMonths(6)); // Far enough for reminder

        ServiceHistory futureHistory = createTestHistory();
        try {
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(futureHistory, LocalDate.now().plusMonths(6));
            java.lang.reflect.Field idField = ServiceHistory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(futureHistory, 2L);
            java.lang.reflect.Field jobIdField = ServiceHistory.class.getDeclaredField("jobId");
            jobIdField.setAccessible(true);
            jobIdField.set(futureHistory, 200L);
            java.lang.reflect.Field vehicleIdField = ServiceHistory.class.getDeclaredField("vehicleId");
            vehicleIdField.setAccessible(true);
            vehicleIdField.set(futureHistory, 20L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(serviceHistoryRepository.existsByJobId(200L)).thenReturn(false);
        when(serviceHistoryRepository.save(any(ServiceHistory.class))).thenReturn(futureHistory);
        when(vehicleCustomerResolver.resolveCustomerId(20L)).thenReturn(2L);

        serviceHistoryService.recordServiceHistory(request);

        ArgumentCaptor<GenerateReminderRequest> reminderCaptor = ArgumentCaptor.forClass(GenerateReminderRequest.class);
        verify(serviceReminderService).generateFromServiceHistory(reminderCaptor.capture());

        GenerateReminderRequest reminderRequest = reminderCaptor.getValue();
        assertEquals(2L, reminderRequest.getServiceHistoryId());
        assertEquals(2L, reminderRequest.getCustomerId());
    }

    @Test
    void recordServiceHistory_withNextServiceDateTooSoon_skipsReminderGeneration() {
        validCreateRequest.setNextServiceDate(LocalDate.now().plusDays(3));

        when(serviceHistoryRepository.existsByJobId(100L)).thenReturn(false);
        when(serviceHistoryRepository.save(any(ServiceHistory.class))).thenReturn(savedHistory);

        serviceHistoryService.recordServiceHistory(validCreateRequest);

        verify(serviceReminderService, never()).generateFromServiceHistory(any());
    }

    @Test
    void recordServiceHistory_withNullNextServiceDate_skipsReminderGeneration() {
        validCreateRequest.setNextServiceDate(null);

        when(serviceHistoryRepository.existsByJobId(100L)).thenReturn(false);
        when(serviceHistoryRepository.save(any(ServiceHistory.class))).thenReturn(savedHistory);

        serviceHistoryService.recordServiceHistory(validCreateRequest);

        verify(serviceReminderService, never()).generateFromServiceHistory(any());
    }

    @Test
    void recordServiceHistory_withResolverReturningNullCustomer_throwsUnprocessableEntity() {
        // Use a date far in the future to trigger reminder logic
        ServiceHistoryCreateRequest request = new ServiceHistoryCreateRequest();
        request.setJobId(300L);
        request.setVehicleId(30L);
        request.setServiceDate(LocalDate.of(2024, 1, 15));
        request.setOdometer(50000);
        request.setSummary("Regular service");
        request.setTechnicianId(5L);
        request.setInvoiceId(200L);
        request.setTotalCost(new BigDecimal("15000.00"));
        request.setNextServiceDate(LocalDate.now().plusMonths(6));

        ServiceHistory futureHistory = createTestHistory();
        try {
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(futureHistory, LocalDate.now().plusMonths(6));
            java.lang.reflect.Field idField = ServiceHistory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(futureHistory, 3L);
            java.lang.reflect.Field jobIdField = ServiceHistory.class.getDeclaredField("jobId");
            jobIdField.setAccessible(true);
            jobIdField.set(futureHistory, 300L);
            java.lang.reflect.Field vehicleIdField = ServiceHistory.class.getDeclaredField("vehicleId");
            vehicleIdField.setAccessible(true);
            vehicleIdField.set(futureHistory, 30L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(serviceHistoryRepository.existsByJobId(300L)).thenReturn(false);
        when(serviceHistoryRepository.save(any(ServiceHistory.class))).thenReturn(futureHistory);
        when(vehicleCustomerResolver.resolveCustomerId(30L)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.recordServiceHistory(request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("No customer is associated"));
    }

    // =====================================================
    // GET ALL SERVICE HISTORIES TESTS
    // =====================================================

    @Test
    void getAllServiceHistories_returnsPagedResults() {
        Page<ServiceHistory> page = new PageImpl<>(List.of(savedHistory));
        when(serviceHistoryRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<ServiceHistoryResponse> response = serviceHistoryService.getAllServiceHistories(0, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(100L, response.getContent().getFirst().getJobId());
    }

    @Test
    void getAllServiceHistories_withInvalidPage_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getAllServiceHistories(-1, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAllServiceHistories_withInvalidSize_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getAllServiceHistories(0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // =====================================================
    // GET HISTORY BY ID TESTS
    // =====================================================

    @Test
    void getServiceHistoryById_withExistingId_returnsHistory() {
        when(serviceHistoryRepository.findById(1L)).thenReturn(Optional.of(savedHistory));

        ServiceHistoryResponse response = serviceHistoryService.getServiceHistoryById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    void getServiceHistoryById_withNonExistentId_throwsNotFound() {
        when(serviceHistoryRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getServiceHistoryById(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not found"));
    }

    // =====================================================
    // GET HISTORY BY VEHICLE TESTS
    // =====================================================

    @Test
    void getServiceHistoryByVehicleId_returnsList() {
        when(serviceHistoryRepository.findByVehicleIdOrderByServiceDateDesc(10L))
                .thenReturn(List.of(savedHistory));

        List<ServiceHistoryResponse> response = serviceHistoryService.getServiceHistoryByVehicleId(10L);

        assertEquals(1, response.size());
        assertEquals(10L, response.getFirst().getVehicleId());
    }

    @Test
    void getServiceHistoryByVehicleId_withInvalidId_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getServiceHistoryByVehicleId(-1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // =====================================================
    // GET HISTORY BY JOB TESTS
    // =====================================================

    @Test
    void getServiceHistoryByJobId_withExistingJob_returnsHistory() {
        when(serviceHistoryRepository.findByJobId(100L)).thenReturn(Optional.of(savedHistory));

        ServiceHistoryResponse response = serviceHistoryService.getServiceHistoryByJobId(100L);

        assertNotNull(response);
        assertEquals(100L, response.getJobId());
    }

    @Test
    void getServiceHistoryByJobId_withNonExistentJob_throwsNotFound() {
        when(serviceHistoryRepository.findByJobId(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getServiceHistoryByJobId(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // =====================================================
    // GET HISTORY BY TECHNICIAN TESTS
    // =====================================================

    @Test
    void getServiceHistoryByTechnician_returnsList() {
        when(serviceHistoryRepository.findByTechnicianIdOrderByServiceDateDesc(5L))
                .thenReturn(List.of(savedHistory));

        List<ServiceHistoryResponse> response = serviceHistoryService.getServiceHistoryByTechnician(5L);

        assertEquals(1, response.size());
        assertEquals(5L, response.getFirst().getTechnicianId());
    }

    // =====================================================
    // GET HISTORY BY DATE RANGE TESTS
    // =====================================================

    @Test
    void getServiceHistoryByDateRange_returnsPagedResults() {
        Page<ServiceHistory> page = new PageImpl<>(List.of(savedHistory));
        when(serviceHistoryRepository.findByServiceDateBetween(any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<ServiceHistoryResponse> response = serviceHistoryService.getServiceHistoryByDateRange(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                0, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void getServiceHistoryByDateRange_withNullDates_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getServiceHistoryByDateRange(null, LocalDate.now(), 0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getServiceHistoryByDateRange_withStartAfterEnd_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.getServiceHistoryByDateRange(
                        LocalDate.of(2024, 12, 31),
                        LocalDate.of(2024, 1, 1),
                        0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // =====================================================
    // UPDATE SERVICE HISTORY TESTS
    // =====================================================

    @Test
    void updateServiceHistory_withValidRequest_updatesAndLogsAudit() {
        ServiceHistoryUpdateRequest updateRequest = new ServiceHistoryUpdateRequest();
        updateRequest.setOdometer(55000);
        updateRequest.setSummary("Updated summary");
        updateRequest.setTotalCost(new BigDecimal("16000.00"));

        when(serviceHistoryRepository.findById(1L)).thenReturn(Optional.of(savedHistory));
        when(serviceHistoryRepository.save(any(ServiceHistory.class))).thenReturn(savedHistory);

        ServiceHistoryResponse response = serviceHistoryService.updateServiceHistory(
                1L, updateRequest, 1L, "Corrected odometer reading", "192.168.1.1");

        assertNotNull(response);
        verify(auditLogService).logEvent(eq(1L), eq("UPDATE"), eq("SERVICE_HISTORY"),
                eq(1L), anyString(), anyString(), anyString(), eq("192.168.1.1"));
    }

    @Test
    void updateServiceHistory_withBlankSummary_throwsBadRequest() {
        ServiceHistoryUpdateRequest request = new ServiceHistoryUpdateRequest();
        request.setSummary("   ");

        when(serviceHistoryRepository.findById(1L)).thenReturn(Optional.of(savedHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(1L, request, 1L, "Reason", "192.168.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Summary cannot be blank", ex.getReason());
    }

    @Test
    void updateServiceHistory_withNextServiceDateBeforeServiceDate_throwsBadRequest() {
        ServiceHistoryUpdateRequest request = new ServiceHistoryUpdateRequest();
        request.setNextServiceDate(LocalDate.of(2024, 1, 10));

        when(serviceHistoryRepository.findById(1L)).thenReturn(Optional.of(savedHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(1L, request, 1L, "Reason", "192.168.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Next service date cannot be"));
    }

    @Test
    void updateServiceHistory_withNullRequest_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(1L, null, 1L, "Reason", "192.168.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateServiceHistory_withNullActorId_throwsBadRequest() {
        ServiceHistoryUpdateRequest request = new ServiceHistoryUpdateRequest();
        request.setOdometer(55000);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(1L, request, null, "Reason", "192.168.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateServiceHistory_withBlankReason_throwsBadRequest() {
        ServiceHistoryUpdateRequest request = new ServiceHistoryUpdateRequest();
        request.setOdometer(55000);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(1L, request, 1L, "   ", "192.168.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Reason for updating service history is required", ex.getReason());
    }

    @Test
    void updateServiceHistory_withNoActualChanges_throwsBadRequest() {
        ServiceHistoryUpdateRequest request = new ServiceHistoryUpdateRequest();
        request.setOdometer(50000);
        request.setSummary("Regular service - oil change, filter replacement");
        request.setTotalCost(new BigDecimal("15000.00"));
        request.setNextServiceDate(LocalDate.of(2024, 7, 15));

        when(serviceHistoryRepository.findById(1L)).thenReturn(Optional.of(savedHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(1L, request, 1L, "Reason", "192.168.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("No service history changes were provided", ex.getReason());
        verify(auditLogService, never()).logEvent(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void updateServiceHistory_withNonExistentId_throwsNotFound() {
        ServiceHistoryUpdateRequest request = new ServiceHistoryUpdateRequest();
        request.setOdometer(55000);

        when(serviceHistoryRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.updateServiceHistory(999L, request, 1L, "Reason", "192.168.1.1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // =====================================================
    // DELETE SERVICE HISTORY TESTS
    // =====================================================

    @Test
    void deleteServiceHistory_withExistingId_deletesHistory() {
        when(serviceHistoryRepository.findById(1L)).thenReturn(Optional.of(savedHistory));

        serviceHistoryService.deleteServiceHistory(1L, 1L, "192.168.1.1");

        verify(serviceHistoryRepository).delete(savedHistory);
        verify(auditLogService).logEvent(eq(1L), eq("DELETE"), eq("SERVICE_HISTORY"),
                eq(1L), anyString(), isNull(), anyString(), eq("192.168.1.1"));
    }

    @Test
    void deleteServiceHistory_withNonExistentId_throwsNotFound() {
        when(serviceHistoryRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceHistoryService.deleteServiceHistory(999L, 1L, "192.168.1.1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // =====================================================
    // MAP TO RESPONSE TESTS
    // =====================================================

    @Test
    void mapToResponse_mapsAllFieldsCorrectly() {
        ServiceHistoryResponse response = serviceHistoryService.mapToResponse(savedHistory);

        assertEquals(1L, response.getId());
        assertEquals(10L, response.getVehicleId());
        assertEquals(100L, response.getJobId());
        assertEquals(LocalDate.of(2024, 1, 15), response.getServiceDate());
        assertEquals(50000, response.getOdometer());
        assertEquals("Regular service - oil change, filter replacement", response.getSummary());
        assertEquals(5L, response.getTechnicianId());
        assertEquals(200L, response.getInvoiceId());
        assertEquals(new BigDecimal("15000.00"), response.getTotalCost());
        assertEquals(LocalDate.of(2024, 7, 15), response.getNextServiceDate());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }
}