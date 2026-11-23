package com.richcomes.garage.notification.service;

import com.richcomes.garage.notification.entity.Notification;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.notification.enums.NotificationType;
import com.richcomes.garage.notification.provider.NotificationProvider;
import com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendRequest;
import com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendResponse;
import com.richcomes.garage.notification.provider.NotificationProvider.FailureDetails;
import com.richcomes.garage.notification.provider.NotificationProvider.FailureCategory;
import com.richcomes.garage.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NotificationDeliveryWorkerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProvider smsProvider;

    @Mock
    private NotificationService notificationService;

    private NotificationDeliveryWorker worker;
    private Notification pendingNotification;

    @BeforeEach
    void setUp() {
        pendingNotification = createPendingNotification(1L, NotificationChannel.SMS, "+94771234567", "Test reminder");
        worker = new NotificationDeliveryWorker(notificationRepository, List.of(smsProvider), notificationService, 50, 1);
    }

    private Notification createPendingNotification(Long id, NotificationChannel channel, String recipient, String message) {
        Notification n = new Notification();
        try {
            java.lang.reflect.Field idField = Notification.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(n, id);
            java.lang.reflect.Field statusField = Notification.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(n, NotificationStatus.PENDING);
            java.lang.reflect.Field channelField = Notification.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(n, channel);
            java.lang.reflect.Field recipientField = Notification.class.getDeclaredField("recipient");
            recipientField.setAccessible(true);
            recipientField.set(n, recipient);
            java.lang.reflect.Field messageField = Notification.class.getDeclaredField("message");
            messageField.setAccessible(true);
            messageField.set(n, message);
            java.lang.reflect.Field typeField = Notification.class.getDeclaredField("type");
            typeField.setAccessible(true);
            typeField.set(n, NotificationType.SERVICE_REMINDER);
            java.lang.reflect.Field retryCountField = Notification.class.getDeclaredField("retryCount");
            retryCountField.setAccessible(true);
            retryCountField.set(n, 0);
            java.lang.reflect.Field nextRetryAtField = Notification.class.getDeclaredField("nextRetryAt");
            nextRetryAtField.setAccessible(true);
            nextRetryAtField.set(n, null);
            java.lang.reflect.Field createdAtField = Notification.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(n, LocalDateTime.now());
            java.lang.reflect.Field updatedAtField = Notification.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(n, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return n;
    }

    // =====================================================
    // PROCESS PENDING NOTIFICATIONS TESTS
    // =====================================================

    @Test
    void processPendingNotifications_noProviders_skipsProcessing() {
        NotificationDeliveryWorker noProviderWorker = new NotificationDeliveryWorker(
                notificationRepository, List.of(), notificationService, 50, 1);

        noProviderWorker.processPendingNotifications();

        verify(notificationRepository, never()).findPendingForProcessing(any(), any(), any());
    }

    @Test
    void processPendingNotifications_noEnabledProviders_skipsProcessing() {
        when(smsProvider.isEnabled()).thenReturn(false);
        NotificationDeliveryWorker worker = new NotificationDeliveryWorker(
                notificationRepository, List.of(smsProvider), notificationService, 50, 1);

        worker.processPendingNotifications();

        verify(notificationRepository, never()).findPendingForProcessing(any(), any(), any());
    }

    @Test
    void processPendingNotifications_noPendingNotifications_returnsEarly() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(notificationRepository.findPendingForProcessing(anyList(), any(), any()))
                .thenReturn(List.of());

        worker.processPendingNotifications();

        verify(notificationRepository).findPendingForProcessing(
                anyList(), any(), any());
    }

    @Test
    void processPendingNotifications_withPendingNotification_processesSuccessfully() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(smsProvider.getMaxRetries()).thenReturn(3);
        when(smsProvider.getBaseBackoffMs()).thenReturn(1000L);
        when(smsProvider.supportsType(NotificationType.SERVICE_REMINDER)).thenReturn(true);

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(pendingNotification));

        ProviderSendResponse successResponse = ProviderSendResponse.of(
                true,
                "provider-msg-123",
                null,
                System.currentTimeMillis()
        );
        when(smsProvider.send(any(ProviderSendRequest.class))).thenReturn(successResponse);

        worker.processPendingNotifications();

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(savedCaptor.capture());

        Notification saved = savedCaptor.getValue();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertEquals("provider-msg-123", saved.getProviderMessageId());
        assertNull(saved.getFailureReason());
        assertEquals(0, saved.getRetryCount());
    }

    // =====================================================
    // RETRY LOGIC TESTS
    // =====================================================

    @Test
    void processPendingNotifications_retryableFailure_schedulesRetry() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(smsProvider.getMaxRetries()).thenReturn(3);
        when(smsProvider.getBaseBackoffMs()).thenReturn(1000L);
        when(smsProvider.supportsType(NotificationType.SERVICE_REMINDER)).thenReturn(true);

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(pendingNotification));

        ProviderSendResponse retryableFailure = ProviderSendResponse.of(
                false,
                null,
                FailureDetails.of(FailureCategory.PROVIDER_UNAVAILABLE, "TIMEOUT", "Gateway timeout", true),
                System.currentTimeMillis()
        );
        when(smsProvider.send(any(ProviderSendRequest.class))).thenReturn(retryableFailure);
        when(smsProvider.isRetryableFailure(retryableFailure)).thenReturn(true);

        worker.processPendingNotifications();

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(savedCaptor.capture());

        Notification saved = savedCaptor.getValue();
        assertEquals(NotificationStatus.PENDING, saved.getStatus()); // Still pending for retry
        assertEquals(1, saved.getRetryCount());
        assertNotNull(saved.getNextRetryAt());
        assertTrue(saved.getNextRetryAt().isAfter(Instant.now()));
    }

    @Test
    void processPendingNotifications_permanentFailure_marksFailed() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(smsProvider.getMaxRetries()).thenReturn(3);
        when(smsProvider.getBaseBackoffMs()).thenReturn(1000L);
        when(smsProvider.supportsType(NotificationType.SERVICE_REMINDER)).thenReturn(true);

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(pendingNotification));

        ProviderSendResponse permanentFailure = ProviderSendResponse.of(
                false,
                null,
                FailureDetails.of(FailureCategory.INVALID_RECIPIENT, "INVALID_NUMBER", "Invalid format", false),
                System.currentTimeMillis()
        );
        when(smsProvider.send(any(ProviderSendRequest.class))).thenReturn(permanentFailure);
        when(smsProvider.isRetryableFailure(permanentFailure)).thenReturn(false);

        worker.processPendingNotifications();

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(savedCaptor.capture());

        Notification saved = savedCaptor.getValue();
        assertEquals(NotificationStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getFailureReason());
    }

    @Test
    void processPendingNotifications_maxRetriesExceeded_marksPermanentFailure() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(smsProvider.getMaxRetries()).thenReturn(3);
        when(smsProvider.getBaseBackoffMs()).thenReturn(1000L);
        when(smsProvider.supportsType(NotificationType.SERVICE_REMINDER)).thenReturn(true);

        // Create notification with retry count exceeding max
        Notification maxRetriesNotification = createPendingNotification(2L, NotificationChannel.SMS, "+94771234567", "Test");
        try {
            java.lang.reflect.Field retryCountField = Notification.class.getDeclaredField("retryCount");
            retryCountField.setAccessible(true);
            retryCountField.set(maxRetriesNotification, 4); // Exceeds max of 3
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(maxRetriesNotification));

        worker.processPendingNotifications();

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(savedCaptor.capture());

        Notification saved = savedCaptor.getValue();
        assertEquals(NotificationStatus.FAILED, saved.getStatus());
        assertTrue(saved.getFailureReason().contains("Exceeded maximum retry attempts"));
    }

    @Test
    void processPendingNotifications_nextRetryNotDue_skipsNotification() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(smsProvider.getMaxRetries()).thenReturn(3);
        when(smsProvider.getBaseBackoffMs()).thenReturn(1000L);
        when(smsProvider.supportsType(NotificationType.SERVICE_REMINDER)).thenReturn(true);

        // Create notification with future retry time
        Notification futureRetryNotification = createPendingNotification(3L, NotificationChannel.SMS, "+94771234567", "Test");
        try {
            java.lang.reflect.Field retryCountField = Notification.class.getDeclaredField("retryCount");
            retryCountField.setAccessible(true);
            retryCountField.set(futureRetryNotification, 1);
            java.lang.reflect.Field nextRetryAtField = Notification.class.getDeclaredField("nextRetryAt");
            nextRetryAtField.setAccessible(true);
            nextRetryAtField.set(futureRetryNotification, Instant.now().plusSeconds(3600)); // 1 hour in future
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(futureRetryNotification));

        worker.processPendingNotifications();

        // Should NOT call provider.send for this notification
        verify(smsProvider, never()).send(any());
        verify(notificationRepository, never()).save(any());
    }

    // =====================================================
    // TYPE SUPPORT TESTS
    // =====================================================

    @Test
    void processPendingNotifications_unsupportedType_marksPermanentFailure() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(smsProvider.getMaxRetries()).thenReturn(3);
        when(smsProvider.getBaseBackoffMs()).thenReturn(1000L);
        when(smsProvider.supportsType(NotificationType.GENERAL_ALERT)).thenReturn(false);

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(pendingNotification));

        worker.processPendingNotifications();

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(savedCaptor.capture());

        Notification saved = savedCaptor.getValue();
        assertEquals(NotificationStatus.FAILED, saved.getStatus());
        assertTrue(saved.getFailureReason().contains("not supported by provider"));
    }

    // =====================================================
    // CHANNEL ROUTING TESTS
    // =====================================================

    @Test
    void processPendingNotifications_noProviderForChannel_skipsNotification() {
        when(smsProvider.isEnabled()).thenReturn(true);
        when(smsProvider.getChannel()).thenReturn(NotificationChannel.SMS);

        // Notification with EMAIL channel but only SMS provider
        Notification emailNotification = createPendingNotification(4L, NotificationChannel.EMAIL, "test@example.com", "Test");

        when(notificationRepository.findPendingForProcessing(any(), any(), any()))
                .thenReturn(List.of(emailNotification));

        worker.processPendingNotifications();

        // Should not process since no provider for EMAIL
        verify(smsProvider, never()).send(any());
    }

    // =====================================================
    // METRICS TESTS
    // =====================================================

    @Test
    void getProcessedCount_returnsCount() {
        assertEquals(0, worker.getProcessedCount());
        assertEquals(0, worker.getFailedCount());
        assertEquals(0, worker.getSkippedCount());
    }
}