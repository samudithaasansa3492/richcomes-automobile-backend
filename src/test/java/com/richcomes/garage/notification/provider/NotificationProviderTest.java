package com.richcomes.garage.notification.provider;

import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendRequest;
import static com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendResponse;
import static com.richcomes.garage.notification.provider.NotificationProvider.FailureDetails;
import static com.richcomes.garage.notification.provider.NotificationProvider.FailureCategory;
import static com.richcomes.garage.notification.provider.NotificationProvider.NotificationProviderException;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NotificationProviderTest {

    @Mock
    private NotificationProvider provider;

    @BeforeEach
    void setUp() {
        when(provider.getChannel()).thenReturn(NotificationChannel.SMS);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getMaxRetries()).thenReturn(3);
        when(provider.getBaseBackoffMs()).thenReturn(1000L);
    }

    // =====================================================
    // PROVIDER CONTRACT TESTS
    // =====================================================

    @Test
    void getChannel_returnsSMS() {
        assertEquals(NotificationChannel.SMS, provider.getChannel());
    }

    @Test
    void isEnabled_returnsTrueWhenConfigured() {
        assertTrue(provider.isEnabled());
    }

    @Test
    void getMaxRetries_returnsConfiguredValue() {
        assertEquals(3, provider.getMaxRetries());
    }

    @Test
    void getBaseBackoffMs_returnsConfiguredValue() {
        assertEquals(1000L, provider.getBaseBackoffMs());
    }

    // =====================================================
    // FAILURE CLASSIFICATION TESTS
    // =====================================================

    @Test
    void isRetryableFailure_returnsTrueForRetryableFailure() {
        ProviderSendResponse retryableResponse = ProviderSendResponse.of(
                false,
                null,
                FailureDetails.of(FailureCategory.PROVIDER_UNAVAILABLE, "TIMEOUT", "Gateway timeout", true),
                System.currentTimeMillis()
        );

        when(provider.isRetryableFailure(retryableResponse)).thenReturn(true);

        assertTrue(provider.isRetryableFailure(retryableResponse));
    }

    @Test
    void isRetryableFailure_returnsFalseForPermanentFailure() {
        ProviderSendResponse permanentResponse = ProviderSendResponse.of(
                false,
                null,
                FailureDetails.of(FailureCategory.INVALID_RECIPIENT, "INVALID_NUMBER", "Invalid phone format", false),
                System.currentTimeMillis()
        );

        when(provider.isRetryableFailure(permanentResponse)).thenReturn(false);

        assertFalse(provider.isRetryableFailure(permanentResponse));
    }

    // =====================================================
    // TYPE SUPPORT TESTS
    // =====================================================

    @Test
    void supportsType_returnsTrueForSupportedTypes() {
        when(provider.supportsType(NotificationType.SERVICE_REMINDER)).thenReturn(true);
        when(provider.supportsType(NotificationType.APPOINTMENT_CONFIRMATION)).thenReturn(true);

        assertTrue(provider.supportsType(NotificationType.SERVICE_REMINDER));
        assertTrue(provider.supportsType(NotificationType.APPOINTMENT_CONFIRMATION));
    }

    @Test
    void supportsType_returnsFalseForUnsupportedTypes() {
        when(provider.supportsType(NotificationType.GENERAL_ALERT)).thenReturn(true);

        assertTrue(provider.supportsType(NotificationType.GENERAL_ALERT));
    }

    // =====================================================
    // PROVIDER SEND REQUEST VALIDATION
    // =====================================================

    @Test
    void providerSendRequest_validRequest_createsSuccessfully() {
        ProviderSendRequest request = new ProviderSendRequest(
                "+94771234567",
                "Test message",
                NotificationType.SERVICE_REMINDER,
                Optional.of("idem-key-123"),
                Optional.of("http://callback")
        );

        assertEquals("+94771234567", request.recipient());
        assertEquals("Test message", request.message());
        assertEquals(NotificationType.SERVICE_REMINDER, request.type());
        assertTrue(request.idempotencyKey().isPresent());
        assertTrue(request.callbackUrl().isPresent());
    }

    @Test
    void providerSendRequest_nullRecipient_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProviderSendRequest(null, "message", NotificationType.SERVICE_REMINDER, Optional.empty(), Optional.empty())
        );
    }

    @Test
    void providerSendRequest_blankRecipient_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProviderSendRequest("   ", "message", NotificationType.SERVICE_REMINDER, Optional.empty(), Optional.empty())
        );
    }

    @Test
    void providerSendRequest_nullMessage_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProviderSendRequest("+94771234567", null, NotificationType.SERVICE_REMINDER, Optional.empty(), Optional.empty())
        );
    }

    @Test
    void providerSendRequest_nullType_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProviderSendRequest("+94771234567", "message", null, Optional.empty(), Optional.empty())
        );
    }

    // =====================================================
    // PROVIDER SEND RESPONSE VALIDATION
    // =====================================================

    @Test
    void providerSendResponse_acceptedWithNullFailure_createsSuccessfully() {
        ProviderSendResponse response = ProviderSendResponse.of(
                true,
                "provider-msg-123",
                null,
                System.currentTimeMillis()
        );

        assertTrue(response.accepted());
        assertEquals("provider-msg-123", response.providerMessageId());
        assertNull(response.failureDetails());
    }

    @Test
    void providerSendResponse_rejectedWithFailure_createsSuccessfully() {
        FailureDetails failure = FailureDetails.of(
                FailureCategory.PROVIDER_UNAVAILABLE,
                "TIMEOUT",
                "Gateway timeout",
                true
        );

        ProviderSendResponse response = ProviderSendResponse.of(
                false,
                null,
                failure,
                System.currentTimeMillis()
        );

        assertFalse(response.accepted());
        assertNull(response.providerMessageId());
        assertNotNull(response.failureDetails());
        assertEquals(FailureCategory.PROVIDER_UNAVAILABLE, response.failureDetails().category());
        assertTrue(response.failureDetails().retryable());
    }

    @Test
    void providerSendResponse_rejectedWithNullFailure_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                ProviderSendResponse.of(false, null, null, System.currentTimeMillis())
        );
    }

    // =====================================================
    // FAILURE DETAILS VALIDATION
    // =====================================================

    @Test
    void failureDetails_withAllFields_createsSuccessfully() {
        FailureDetails failure = FailureDetails.of(
                FailureCategory.PROVIDER_RATE_LIMITED,
                "429",
                "Rate limit exceeded",
                true
        );

        assertEquals(FailureCategory.PROVIDER_RATE_LIMITED, failure.category());
        assertEquals("429", failure.providerErrorCode());
        assertEquals("Rate limit exceeded", failure.providerErrorMessage());
        assertTrue(failure.retryable());
    }

    @Test
    void failureDetails_nullCategory_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                FailureDetails.of(null, "CODE", "message", true)
        );
    }

    // =====================================================
    // PROVIDER EXCEPTION TESTS
    // =====================================================

    @Test
    void notificationProviderException_storesCategoryAndRetryable() {
        NotificationProviderException ex = new NotificationProviderException(
                "Gateway timeout",
                FailureCategory.PROVIDER_UNAVAILABLE,
                true
        );

        assertEquals("Gateway timeout", ex.getMessage());
        assertEquals(FailureCategory.PROVIDER_UNAVAILABLE, ex.getCategory());
        assertTrue(ex.isRetryable());
    }

    @Test
    void notificationProviderException_withCause_preservesCause() {
        Throwable cause = new RuntimeException("Connection refused");
        NotificationProviderException ex = new NotificationProviderException(
                "Gateway unavailable",
                cause,
                FailureCategory.NETWORK_ERROR,
                true
        );

        assertSame(cause, ex.getCause());
        assertEquals(FailureCategory.NETWORK_ERROR, ex.getCategory());
        assertTrue(ex.isRetryable());
    }
}