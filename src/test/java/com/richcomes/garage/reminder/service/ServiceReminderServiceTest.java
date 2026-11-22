package com.richcomes.garage.reminder.service;

import com.richcomes.garage.audit.service.AuditLogService;
import com.richcomes.garage.history.entity.ServiceHistory;
import com.richcomes.garage.history.repository.ServiceHistoryRepository;
import com.richcomes.garage.notification.service.NotificationService;
import com.richcomes.garage.notification.dto.NotificationSendRequest;
import com.richcomes.garage.notification.dto.NotificationResponse;
import com.richcomes.garage.notification.enums.NotificationType;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.reminder.dto.ServiceReminderCreateRequest;
import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import com.richcomes.garage.reminder.dto.ServiceReminderUpdateRequest;
import com.richcomes.garage.reminder.dto.GenerateReminderRequest;
import com.richcomes.garage.reminder.entity.ServiceReminder;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import com.richcomes.garage.reminder.repository.ServiceReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceReminderServiceTest {

    @Mock
    private ServiceReminderRepository reminderRepository;

    @Mock
    private ServiceHistoryRepository serviceHistoryRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ServiceReminderService reminderService;

    private ServiceReminderCreateRequest validCreateRequest;
    private ServiceReminder savedReminder;
    private ServiceHistory sampleHistory;
    private ZoneId businessZone = ZoneId.of("Asia/Colombo");

    @BeforeEach
    void setUp() {
        validCreateRequest = new ServiceReminderCreateRequest();
        validCreateRequest.setVehicleId(10L);
        validCreateRequest.setCustomerId(1L);
        validCreateRequest.setServiceHistoryId(100L);
        validCreateRequest.setNextServiceDate(LocalDate.of(2024, 7, 15));
        validCreateRequest.setReminderDate(LocalDate.of(2024, 7, 8)); // 7 days before
        validCreateRequest.setMessage("Your service is due soon");
        validCreateRequest.setAiSuggestedDate(null);

        savedReminder = createTestReminder();

        sampleHistory = new ServiceHistory();
        try {
            java.lang.reflect.Field idField = ServiceHistory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(sampleHistory, 100L);
            java.lang.reflect.Field vehicleIdField = ServiceHistory.class.getDeclaredField("vehicleId");
            vehicleIdField.setAccessible(true);
            vehicleIdField.set(sampleHistory, 10L);
            java.lang.reflect.Field odometerField = ServiceHistory.class.getDeclaredField("odometer");
            odometerField.setAccessible(true);
            odometerField.set(sampleHistory, 50000);
            java.lang.reflect.Field serviceDateField = ServiceHistory.class.getDeclaredField("serviceDate");
            serviceDateField.setAccessible(true);
            serviceDateField.set(sampleHistory, LocalDate.of(2024, 1, 15));
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(sampleHistory, LocalDate.of(2024, 7, 15));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =====================================================
    // CREATE REMINDER TESTS
    // =====================================================

    @Test
    void createReminder_withValidRequest_createsAndReturnsReminder() {
        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));
        when(reminderRepository.findByServiceHistoryId(100L)).thenReturn(List.of());
        when(reminderRepository.save(any(ServiceReminder.class))).thenReturn(savedReminder);

        ServiceReminderResponse response = reminderService.createReminder(validCreateRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(10L, response.getVehicleId());
        assertEquals(1L, response.getCustomerId());
        assertEquals(ServiceReminderStatus.PENDING, response.getStatus());
        verify(reminderRepository).save(any(ServiceReminder.class));
    }

    @Test
    void createReminder_withNullRequest_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Reminder request is required", ex.getReason());
    }

    @Test
    void createReminder_withNullVehicleId_throwsBadRequest() {
        validCreateRequest.setVehicleId(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Vehicle ID and customer ID are required"));
    }

    @Test
    void createReminder_withNullCustomerId_throwsBadRequest() {
        validCreateRequest.setCustomerId(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createReminder_withNullReminderDate_throwsBadRequest() {
        validCreateRequest.setReminderDate(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Reminder date and next service date are required"));
    }

    @Test
    void createReminder_withNullNextServiceDate_throwsBadRequest() {
        validCreateRequest.setNextServiceDate(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createReminder_withIncorrectReminderDateOffset_throwsBadRequest() {
        // Reminder date must be exactly 7 days before next service date
        validCreateRequest.setReminderDate(LocalDate.of(2024, 7, 7)); // 8 days before

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("exactly 7 days before"));
    }

    @Test
    void createReminder_withBlankMessage_throwsBadRequest() {
        validCreateRequest.setMessage("   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Reminder message cannot be blank", ex.getReason());
    }

    @Test
    void createReminder_withServiceHistoryId_validatesVehicleMatch() {
        validCreateRequest.setVehicleId(20L); // Different from history's vehicle (10L)

        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Vehicle does not match service history"));
    }

    @Test
    void createReminder_withDuplicateServiceHistory_throwsConflict() {
        when(reminderRepository.findByServiceHistoryId(100L)).thenReturn(List.of(savedReminder));
        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.createReminder(validCreateRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists for this service history"));
    }

    // =====================================================
    // GENERATE FROM SERVICE HISTORY TESTS
    // =====================================================

    @Test
    void generateFromServiceHistory_withValidRequest_createsReminder() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(100L);
        genRequest.setCustomerId(1L);

        // Update sampleHistory with future next service date
        try {
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(sampleHistory, LocalDate.now(businessZone).plusMonths(6));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));
        when(reminderRepository.findByServiceHistoryId(100L)).thenReturn(List.of());
        when(reminderRepository.save(any(ServiceReminder.class))).thenReturn(savedReminder);

        ServiceReminderResponse response = reminderService.generateFromServiceHistory(genRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(100L, response.getServiceHistoryId());
        assertEquals(ServiceReminderStatus.PENDING, response.getStatus());
        verify(reminderRepository).save(any(ServiceReminder.class));
    }

    @Test
    void generateFromServiceHistory_withNullRequest_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateFromServiceHistory_withNullServiceHistoryId_throwsBadRequest() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setCustomerId(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateFromServiceHistory_withNullCustomerId_throwsBadRequest() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(100L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateFromServiceHistory_withNonExistentHistory_throwsNotFound() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(999L);
        genRequest.setCustomerId(1L);

        when(serviceHistoryRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void generateFromServiceHistory_withDuplicateHistory_throwsConflict() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(100L);
        genRequest.setCustomerId(1L);

        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));
        when(reminderRepository.findByServiceHistoryId(100L)).thenReturn(List.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists for this service history"));
    }

    @Test
    void generateFromServiceHistory_withMissingNextServiceDate_throwsBadRequest() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(100L);
        genRequest.setCustomerId(1L);

        // History without next service date
        try {
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(sampleHistory, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Next service date is missing"));
    }

    @Test
    void generateFromServiceHistory_withPastNextServiceDate_throwsBadRequest() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(100L);
        genRequest.setCustomerId(1L);

        try {
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(sampleHistory, LocalDate.now(businessZone).minusDays(1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already passed"));
    }

    @Test
    void generateFromServiceHistory_withReminderDateAlreadyPassed_throwsBadRequest() {
        GenerateReminderRequest genRequest = new GenerateReminderRequest();
        genRequest.setServiceHistoryId(100L);
        genRequest.setCustomerId(1L);

        // Next service date is 5 days from now, so reminder date (7 days before) is in the past
        try {
            java.lang.reflect.Field nextServiceDateField = ServiceHistory.class.getDeclaredField("nextServiceDate");
            nextServiceDateField.setAccessible(true);
            nextServiceDateField.set(sampleHistory, LocalDate.now(businessZone).plusDays(5));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(serviceHistoryRepository.findById(100L)).thenReturn(Optional.of(sampleHistory));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.generateFromServiceHistory(genRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("reminder date has already passed"));
    }

    // =====================================================
    // GET REMINDERS TESTS
    // =====================================================

    @Test
    void getAllReminders_withStatus_returnsFilteredList() {
        when(reminderRepository.findByStatusOrderByReminderDateAsc(ServiceReminderStatus.PENDING))
                .thenReturn(List.of(savedReminder));

        List<ServiceReminderResponse> response = reminderService.getAllReminders(ServiceReminderStatus.PENDING);

        assertEquals(1, response.size());
        assertEquals(ServiceReminderStatus.PENDING, response.getFirst().getStatus());
    }

    @Test
    void getAllReminders_withoutStatus_returnsAll() {
        when(reminderRepository.findAll()).thenReturn(List.of(savedReminder));

        List<ServiceReminderResponse> response = reminderService.getAllReminders(null);

        assertEquals(1, response.size());
    }

    @Test
    void getReminderById_withExistingId_returnsReminder() {
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ServiceReminderResponse response = reminderService.getReminderById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    void getRemindersByVehicle_returnsList() {
        when(reminderRepository.findByVehicleIdOrderByNextServiceDateAsc(10L))
                .thenReturn(List.of(savedReminder));

        List<ServiceReminderResponse> response = reminderService.getRemindersByVehicle(10L);

        assertEquals(1, response.size());
    }

    @Test
    void getRemindersByCustomer_returnsList() {
        when(reminderRepository.findByCustomerIdOrderByReminderDateAsc(1L))
                .thenReturn(List.of(savedReminder));

        List<ServiceReminderResponse> response = reminderService.getRemindersByCustomer(1L);

        assertEquals(1, response.size());
    }

    @Test
    void getDueReminders_returnsDueRemindersWithoutNotifications() {
        when(reminderRepository.findByStatusAndNotificationIdIsNullAndReminderDateLessThanEqual(
                eq(ServiceReminderStatus.PENDING), any(LocalDate.class)))
                .thenReturn(List.of(savedReminder));

        List<ServiceReminderResponse> response = reminderService.getDueReminders();

        assertEquals(1, response.size());
    }

    // =====================================================
    // SEND REMINDER TESTS
    // =====================================================

    @Test
    void sendReminder_withValidRequest_queuesNotification() {
        NotificationResponse notificationResponse = new NotificationResponse();
        notificationResponse.setId(500L);
        notificationResponse.setStatus(NotificationStatus.PENDING);

        when(reminderRepository.findLockedById(1L)).thenReturn(Optional.of(savedReminder));
        when(notificationService.sendNotification(any(NotificationSendRequest.class)))
                .thenReturn(notificationResponse);
        when(reminderRepository.save(any(ServiceReminder.class))).thenReturn(savedReminder);

        ServiceReminderResponse response = reminderService.sendReminder(1L, "+94771234567");

        assertNotNull(response);
        assertEquals(500L, response.getNotificationId());
        verify(notificationService).sendNotification(any(NotificationSendRequest.class));
    }

    @Test
    void sendReminder_withNonExistentId_throwsNotFound() {
        when(reminderRepository.findLockedById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.sendReminder(999L, "+94771234567"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void sendReminder_withAlreadyQueuedNotification_throwsConflict() {
        savedReminder.setNotificationId(500L);
        when(reminderRepository.findLockedById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.sendReminder(1L, "+94771234567"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already been queued"));
    }

    @Test
    void sendReminder_withNonPendingStatus_throwsConflict() {
        savedReminder.setStatus(ServiceReminderStatus.SENT);
        when(reminderRepository.findLockedById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.sendReminder(1L, "+94771234567"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Only pending reminders can be sent"));
    }

    @Test
    void sendReminder_withFutureReminderDate_throwsConflict() {
        savedReminder.setReminderDate(LocalDate.now(businessZone).plusDays(1));
        when(reminderRepository.findLockedById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.sendReminder(1L, "+94771234567"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not due yet"));
    }

    @Test
    void sendReminder_withBlankRecipient_throwsBadRequest() {
        when(reminderRepository.findLockedById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.sendReminder(1L, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Customer phone number is required"));
    }

    @Test
    void sendReminder_withFailedNotificationValidation_keepsReminderEligible() {
        NotificationResponse failedResponse = new NotificationResponse();
        failedResponse.setId(500L);
        failedResponse.setStatus(NotificationStatus.FAILED);

        when(reminderRepository.findLockedById(1L)).thenReturn(Optional.of(savedReminder));
        when(notificationService.sendNotification(any(NotificationSendRequest.class)))
                .thenReturn(failedResponse);

        ServiceReminderResponse response = reminderService.sendReminder(1L, "invalid-phone");

        // Notification ID should NOT be set on reminder
        assertNull(response.getNotificationId());
        verify(reminderRepository, never()).save(any());
    }

    // =====================================================
    // CANCEL REMINDER TESTS
    // =====================================================

    @Test
    void cancelReminder_withValidPendingReminder_cancels() {
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));
        when(reminderRepository.save(any(ServiceReminder.class))).thenReturn(savedReminder);

        ServiceReminderResponse response = reminderService.cancelReminder(1L);

        assertEquals(ServiceReminderStatus.CANCELLED, response.getStatus());
    }

    @Test
    void cancelReminder_withNonPendingStatus_throwsConflict() {
        savedReminder.setStatus(ServiceReminderStatus.SENT);
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.cancelReminder(1L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Only pending reminders can be cancelled"));
    }

    @Test
    void cancelReminder_withQueuedNotification_throwsConflict() {
        savedReminder.setNotificationId(500L);
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.cancelReminder(1L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cancel it through the Notification module"));
    }

    // =====================================================
    // UPDATE REMINDER TESTS
    // =====================================================

    @Test
    void updateReminder_withValidRequest_updatesReminder() {
        ServiceReminderUpdateRequest updateRequest = new ServiceReminderUpdateRequest();
        updateRequest.setNextServiceDate(LocalDate.of(2024, 8, 15));
        updateRequest.setReminderDate(LocalDate.of(2024, 8, 8));
        updateRequest.setMessage("Updated message");
        updateRequest.setAiSuggestedDate(LocalDate.of(2024, 8, 15));

        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));
        when(reminderRepository.save(any(ServiceReminder.class))).thenReturn(savedReminder);

        ServiceReminderResponse response = reminderService.updateReminder(1L, updateRequest);

        assertNotNull(response);
        verify(reminderRepository).save(any(ServiceReminder.class));
    }

    @Test
    void updateReminder_withNonPendingStatus_throwsConflict() {
        savedReminder.setStatus(ServiceReminderStatus.SENT);
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ServiceReminderUpdateRequest updateRequest = new ServiceReminderUpdateRequest();
        updateRequest.setNextServiceDate(LocalDate.of(2024, 8, 15));
        updateRequest.setReminderDate(LocalDate.of(2024, 8, 8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.updateReminder(1L, updateRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateReminder_withIncorrectDateOffset_throwsBadRequest() {
        ServiceReminderUpdateRequest updateRequest = new ServiceReminderUpdateRequest();
        updateRequest.setNextServiceDate(LocalDate.of(2024, 8, 15));
        updateRequest.setReminderDate(LocalDate.of(2024, 8, 7)); // 8 days, not 7

        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.updateReminder(1L, updateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("exactly 7 days before"));
    }

    @Test
    void updateReminder_withBlankMessage_throwsBadRequest() {
        ServiceReminderUpdateRequest updateRequest = new ServiceReminderUpdateRequest();
        updateRequest.setNextServiceDate(LocalDate.of(2024, 8, 15));
        updateRequest.setReminderDate(LocalDate.of(2024, 8, 8));
        updateRequest.setMessage("   ");

        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.updateReminder(1L, updateRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Reminder message cannot be blank", ex.getReason());
    }

    @Test
    void updateReminder_withQueuedNotification_throwsConflict() {
        savedReminder.setNotificationId(500L);
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ServiceReminderUpdateRequest updateRequest = new ServiceReminderUpdateRequest();
        updateRequest.setNextServiceDate(LocalDate.of(2024, 8, 15));
        updateRequest.setReminderDate(LocalDate.of(2024, 8, 8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.updateReminder(1L, updateRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Cannot edit a reminder after its notification is queued"));
    }

    // =====================================================
    // DELETE REMINDER TESTS
    // =====================================================

    @Test
    void deleteReminder_alwaysThrowsConflict() {
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(savedReminder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reminderService.deleteReminder(1L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cannot be deleted"));
    }

    // =====================================================
    // MAP TO RESPONSE TESTS
    // =====================================================

    @Test
    void mapToResponse_mapsAllFields() {
        ServiceReminderResponse response = reminderService.mapToResponse(savedReminder);

        assertEquals(1L, response.getId());
        assertEquals(10L, response.getVehicleId());
        assertEquals(1L, response.getCustomerId());
        assertEquals(100L, response.getServiceHistoryId());
        assertEquals(LocalDate.of(2024, 7, 15), response.getNextServiceDate());
        assertEquals(LocalDate.of(2024, 7, 8), response.getReminderDate());
        assertEquals(ServiceReminderStatus.PENDING, response.getStatus());
        assertEquals("Your service is due soon", response.getMessage());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    private ServiceReminder createTestReminder() {
        ServiceReminder reminder = new ServiceReminder();
        reminder.setVehicleId(10L);
        reminder.setCustomerId(1L);
        reminder.setServiceHistoryId(100L);
        reminder.setNextServiceDate(LocalDate.of(2024, 7, 15));
        reminder.setReminderDate(LocalDate.of(2024, 7, 8));
        reminder.setMessage("Your service is due soon");
        reminder.setStatus(ServiceReminderStatus.PENDING);
        // id, createdAt, updatedAt are set by JPA @PrePersist
        try {
            java.lang.reflect.Field idField = ServiceReminder.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(reminder, 1L);
            java.lang.reflect.Field createdAtField = ServiceReminder.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(reminder, LocalDateTime.now());
            java.lang.reflect.Field updatedAtField = ServiceReminder.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(reminder, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return reminder;
    }
}