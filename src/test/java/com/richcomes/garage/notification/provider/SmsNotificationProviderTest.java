package com.richcomes.garage.notification.provider;

import com.richcomes.garage.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import com.richcomes.garage.notification.enums.NotificationChannel;

import static org.junit.jupiter.api.Assertions.*;
import static com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendRequest;
import static com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendResponse;
import static com.richcomes.garage.notification.provider.NotificationProvider.FailureDetails;
import static com.richcomes.garage.notification.provider.NotificationProvider.FailureCategory;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SmsNotificationProviderTest {

    private SmsNotificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SmsNotificationProvider();
        // Disable by default for tests
        ReflectionTestUtils.setField(provider, "enabled", false);
        ReflectionTestUtils.setField(provider, "providerType", "disabled");
        ReflectionTestUtils.setField(provider, "maxRetries", 3);
        ReflectionTestUtils.setField(provider, "baseBackoffMs", 1000L);
    }

    // =====================================================
    // CHANNEL AND ENABLED STATE TESTS
    // =====================================================

    @Test
    void getChannel_returnsSMS() {
        assertEquals(NotificationChannel.SMS, provider.getChannel());
    }

    @Test
    void isEnabled_returnsFalseWhenDisabled() {
        assertFalse(provider.isEnabled());
    }

    @Test
    void isEnabled_returnsFalseWhenProviderTypeDisabled() {
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "providerType", "disabled");
        assertFalse(provider.isEnabled());
    }

    @Test
    void isEnabled_returnsFalseWhenTwilioConfigMissing() {
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "providerType", "twilio");
        ReflectionTestUtils.setField(provider, "twilioAccountSid", "");
        assertFalse(provider.isEnabled());
    }

    @Test
    void isEnabled_returnsFalseWhenHttpConfigMissing() {
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "providerType", "http");
        ReflectionTestUtils.setField(provider, "httpUrl", "");
        assertFalse(provider.isEnabled());
    }

    // =====================================================
    // RETRY CONFIGURATION TESTS
    // =====================================================

    @Test
    void getMaxRetries_returnsConfiguredValue() {
        ReflectionTestUtils.setField(provider, "maxRetries", 5);
        assertEquals(5, provider.getMaxRetries());
    }

    @Test
    void getBaseBackoffMs_returnsConfiguredValue() {
        ReflectionTestUtils.setField(provider, "baseBackoffMs", 2000L);
        assertEquals(2000L, provider.getBaseBackoffMs());
    }

    // =====================================================
    // SEND TESTS - DISABLED PROVIDER
    // =====================================================

    @Test
    void send_whenDisabled_returnsPermanentFailure() {
        ProviderSendRequest request = new ProviderSendRequest(
                "+94771234567",
                "Test message",
                NotificationType.SERVICE_REMINDER,
                Optional.of("idem-1"),
                Optional.empty()
        );

        ProviderSendResponse response = provider.send(request);

        assertFalse(response.accepted());
        assertNull(response.providerMessageId());
        assertNotNull(response.failureDetails());
        assertEquals(FailureCategory.PROVIDER_UNAVAILABLE, response.failureDetails().category());
        assertFalse(response.failureDetails().retryable());
    }

    // =====================================================
    // SEND TESTS - RECIPIENT VALIDATION
    // =====================================================

    @Test
    void send_invalidFormat_returnsPermanentFailure() {
        // Enable but with invalid config to test validation
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "providerType", "http");
        ReflectionTestUtils.setField(provider, "httpUrl", "http://test");
        ReflectionTestUtils.setField(provider, "httpApiKey", "key");
        ReflectionTestUtils.setField(provider, "httpFrom", "test");

        ProviderSendRequest request = new ProviderSendRequest(
                "invalid-number",
                "Test message",
                NotificationType.SERVICE_REMINDER,
                Optional.of("idem-1"),
                Optional.empty()
        );

        ProviderSendResponse response = provider.send(request);

        assertFalse(response.accepted());
        assertEquals(FailureCategory.INVALID_RECIPIENT, response.failureDetails().category());
        assertFalse(response.failureDetails().retryable());
    }

    @Test
    void send_validSriLankanFormat_passesFormatCheck() {
        // We can't test actual send without real provider, but we can test validation passes
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "providerType", "http");
        ReflectionTestUtils.setField(provider, "httpUrl", "http://test");
        ReflectionTestUtils.setField(provider, "httpApiKey", "key");
        ReflectionTestUtils.setField(provider, "httpFrom", "test");

        ProviderSendRequest request = new ProviderSendRequest(
                "+94771234567",
                "Test message",
                NotificationType.SERVICE_REMINDER,
                Optional.of("idem-1"),
                Optional.empty()
        );

        ProviderSendResponse response = provider.send(request);

        // Will fail at provider implementation, but not at validation
        // The response should be a provider error, not validation error
        assertFalse(response.accepted());
        // Should NOT be INVALID_RECIPIENT since format is valid
        assertNotEquals(FailureCategory.INVALID_RECIPIENT, response.failureDetails().category());
    }

    @Test
    void send_messageTooLong_returnsPermanentFailure() {
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "providerType", "http");
        ReflectionTestUtils.setField(provider, "httpUrl", "http://test");
        ReflectionTestUtils.setField(provider, "httpApiKey", "key");
        ReflectionTestUtils.setField(provider, "httpFrom", "test");

        String longMessage = "x".repeat(1601);
        ProviderSendRequest request = new ProviderSendRequest(
                "+94771234567",
                longMessage,
                NotificationType.SERVICE_REMINDER,
                Optional.of("idem-1"),
                Optional.empty()
        );

        ProviderSendResponse response = provider.send(request);

        assertFalse(response.accepted());
        assertEquals(FailureCategory.INVALID_MESSAGE, response.failureDetails().category());
        assertFalse(response.failureDetails().retryable());
    }

    // =====================================================
    // TYPE SUPPORT TESTS
    // =====================================================

    @Test
    void supportsType_returnsTrueForSupportedTypes() {
        assertTrue(provider.supportsType(NotificationType.SERVICE_REMINDER));
        assertTrue(provider.supportsType(NotificationType.APPOINTMENT_CONFIRMATION));
        assertTrue(provider.supportsType(NotificationType.JOB_STATUS_UPDATE));
        assertTrue(provider.supportsType(NotificationType.INVOICE_READY));
        assertTrue(provider.supportsType(NotificationType.PAYMENT_RECEIPT));
        assertTrue(provider.supportsType(NotificationType.GENERAL_ALERT));
    }

    // =====================================================
    // FAILURE CLASSIFICATION TESTS
    // =====================================================

    @Test
    void isRetryableFailure_returnsTrueForRetryableFailure() {
        ProviderSendResponse retryable = ProviderSendResponse.of(
                false, null,
                FailureDetails.of(FailureCategory.PROVIDER_UNAVAILABLE, "TIMEOUT", "Timeout", true),
                System.currentTimeMillis()
        );

        assertTrue(provider.isRetryableFailure(retryable));
    }

    @Test
    void isRetryableFailure_returnsFalseForPermanentFailure() {
        ProviderSendResponse permanent = ProviderSendResponse.of(
                false, null,
                FailureDetails.of(FailureCategory.INVALID_RECIPIENT, "INVALID", "Invalid", false),
                System.currentTimeMillis()
        );

        assertFalse(provider.isRetryableFailure(permanent));
    }
}