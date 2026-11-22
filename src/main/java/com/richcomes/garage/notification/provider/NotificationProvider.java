package com.richcomes.garage.notification.provider;

import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationType;

import java.util.Optional;

/**
 * Provider-neutral interface for notification delivery.
 * Implementations handle communication with external providers (SMS gateways, email services, etc.)
 *
 * Design principles:
 * - Provider-neutral: no provider-specific concepts leak into callers
 * - Idempotency support: implementations should support idempotent sends where provider allows
 * - Failure classification: clearly distinguish retryable vs permanent failures
 * - No credentials in interface: credentials handled via configuration injection
 * - Observable: implementations should support structured logging without exposing secrets
 */
public interface NotificationProvider {

    /**
     * Get the channel this provider handles.
     * @return the notification channel (SMS, EMAIL, etc.)
     */
    NotificationChannel getChannel();

    /**
     * Check if this provider is enabled and configured.
     * @return true if provider can accept sends, false if disabled/misconfigured
     */
    boolean isEnabled();

    /**
     * Send a notification through this provider.
     *
     * @param request the notification request containing recipient, message, etc.
     * @return provider response with acceptance status, provider message ID, and failure details
     * @throws NotificationProviderException if provider communication fails
     */
    ProviderSendResponse send(ProviderSendRequest request);

    /**
     * Get the maximum number of retry attempts for this provider.
     * @return max retries, or 0 for no retries
     */
    int getMaxRetries();

    /**
     * Get the base backoff interval in milliseconds.
     * @return backoff in ms
     */
    long getBaseBackoffMs();

    /**
     * Determine if a failure response indicates a retryable error.
     * @param response the provider response indicating failure
     * @return true if the failure is retryable, false if permanent
     */
    boolean isRetryableFailure(ProviderSendResponse response);

    /**
     * Determine if a notification type is supported by this provider.
     * @param type the notification type to check
     * @return true if supported
     */
    boolean supportsType(NotificationType type);

    // =====================================================
    // DATA CLASSES
    // =====================================================

    /**
     * Request to send a notification via provider.
     */
    record ProviderSendRequest(
            String recipient,
            String message,
            NotificationType type,
            Optional<String> idempotencyKey,
            Optional<String> callbackUrl
    ) {
        public ProviderSendRequest {
            if (recipient == null || recipient.isBlank()) {
                throw new IllegalArgumentException("Recipient is required");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Message is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("Notification type is required");
            }
        }
    }

    /**
     * Response from provider after send attempt.
     */
    record ProviderSendResponse(
            boolean accepted,
            String providerMessageId,
            FailureDetails failureDetails,
            long timestamp
    ) {
        public ProviderSendResponse {
            if (!accepted && failureDetails == null) {
                throw new IllegalArgumentException("Failure details required when not accepted");
            }
        }

        public static ProviderSendResponse of(boolean accepted, String providerMessageId,
                                              FailureDetails failureDetails, long timestamp) {
            return new ProviderSendResponse(accepted, providerMessageId, failureDetails, timestamp);
        }
    }

    /**
     * Structured failure details for classification.
     */
    record FailureDetails(
            FailureCategory category,
            String providerErrorCode,
            String providerErrorMessage,
            boolean retryable
    ) {
        public FailureDetails {
            if (category == null) {
                throw new IllegalArgumentException("Failure category is required");
            }
        }

        public static FailureDetails of(FailureCategory category, String providerErrorCode,
                                        String providerErrorMessage, boolean retryable) {
            return new FailureDetails(category, providerErrorCode, providerErrorMessage, retryable);
        }
    }

    /**
     * Failure category for routing and retry decisions.
     */
    enum FailureCategory {
        // Retryable failures
        PROVIDER_UNAVAILABLE,      // Gateway down, timeout, connection refused
        PROVIDER_RATE_LIMITED,     // 429 Too Many Requests, quota exceeded
        PROVIDER_TEMPORARY_ERROR,  // 5xx errors, transient issues
        NETWORK_ERROR,             // DNS, connection reset, SSL issues

        // Permanent failures
        INVALID_RECIPIENT,         // Number/email format invalid, blocked, unsubscribed
        INVALID_MESSAGE,           // Content too long, encoding issues, blocked content
        AUTHENTICATION_FAILED,     // Invalid API key, account suspended
        ACCOUNT_ERROR,             // Insufficient balance, account closed
        PROVIDER_REJECTED,         // Permanent rejection not covered above
        UNKNOWN                    // Could not classify
    }

    /**
     * Exception for provider communication failures.
     */
    class NotificationProviderException extends RuntimeException {
        private final FailureCategory category;
        private final boolean retryable;

        public NotificationProviderException(String message, FailureCategory category, boolean retryable) {
            super(message);
            this.category = category;
            this.retryable = retryable;
        }

        public NotificationProviderException(String message, Throwable cause, FailureCategory category, boolean retryable) {
            super(message, cause);
            this.category = category;
            this.retryable = retryable;
        }

        public FailureCategory getCategory() { return category; }
        public boolean isRetryable() { return retryable; }
    }
}