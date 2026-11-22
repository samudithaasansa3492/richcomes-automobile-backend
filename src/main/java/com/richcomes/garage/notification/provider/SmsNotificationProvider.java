package com.richcomes.garage.notification.provider;

import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SMS notification provider adapter.
 *
 * This adapter is DISABLED by default and requires explicit configuration to enable.
 *
 * To activate real SMS delivery:
 * 1. Set 'notification.sms.enabled=true' in application properties
 * 2. Configure provider-specific properties:
 *    - For Twilio: notification.sms.twilio.account-sid, notification.sms.twilio.auth-token, notification.sms.twilio.from-number
 *    - For generic HTTP: notification.sms.http.url, notification.sms.http.api-key, notification.sms.http.from
 * 3. Ensure Member 02 provides verified mobile and consent contract
 *
 * Current implementation:
 * - Validates configuration on startup
 * - Returns PERMANENT failure if disabled
 * - Supports idempotent sends via idempotency key
 * - Classifies failures according to provider contract
 */
@Component
@Profile("!test") // Disabled in test profile
public class SmsNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationProvider.class);

    // Sri Lankan mobile format validation
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^(?:\\+94|0)7[0-9]{8}$");

    @Value("${notification.sms.enabled:false}")
    private boolean enabled;

    @Value("${notification.sms.provider:disabled}")
    private String providerType; // "twilio", "http", "disabled"

    @Value("${notification.sms.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${notification.sms.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${notification.sms.twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${notification.sms.http.url:}")
    private String httpUrl;

    @Value("${notification.sms.http.api-key:}")
    private String httpApiKey;

    @Value("${notification.sms.http.from:}")
    private String httpFrom;

    @Value("${notification.sms.max-retries:3}")
    private int maxRetries;

    @Value("${notification.sms.base-backoff-ms:1000}")
    private long baseBackoffMs;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public boolean isEnabled() {
        if (!enabled) {
            log.debug("SMS provider disabled via configuration");
            return false;
        }
        if ("disabled".equalsIgnoreCase(providerType)) {
            log.debug("SMS provider type is 'disabled'");
            return false;
        }
        if (!validateConfiguration()) {
            log.warn("SMS provider enabled but configuration invalid");
            return false;
        }
        return true;
    }

    @Override
    public ProviderSendResponse send(ProviderSendRequest request) {
        if (!isEnabled()) {
            return ProviderSendResponse.of(
                    false,
                    null,
                    FailureDetails.of(FailureCategory.PROVIDER_UNAVAILABLE, "PROVIDER_DISABLED",
                            "SMS provider is not enabled or not configured", false),
                    Instant.now().toEpochMilli()
            );
        }

        // Validate recipient format
        String recipient = request.recipient().trim();
        if (!MOBILE_PATTERN.matcher(recipient).matches()) {
            return ProviderSendResponse.of(
                    false,
                    null,
                    FailureDetails.of(FailureCategory.INVALID_RECIPIENT, "INVALID_FORMAT",
                            "Recipient phone number format invalid for Sri Lanka: " + maskPhone(recipient), false),
                    Instant.now().toEpochMilli()
            );
        }

        // Validate message length (standard SMS limit)
        if (request.message().length() > 1600) { // Allow concatenated SMS
            return ProviderSendResponse.of(
                    false,
                    null,
                    FailureDetails.of(FailureCategory.INVALID_MESSAGE, "MESSAGE_TOO_LONG",
                            "Message exceeds maximum length", false),
                    Instant.now().toEpochMilli()
            );
        }

        try {
            String providerMessageId = sendViaProvider(recipient, request.message(), request.idempotencyKey().orElse(null));

            log.info("SMS sent via {}: providerMsgId={}, recipient={}",
                    providerType, providerMessageId, maskPhone(recipient));

            return ProviderSendResponse.of(
                    true,
                    providerMessageId,
                    null,
                    Instant.now().toEpochMilli()
            );

        } catch (NotificationProviderException e) {
            log.warn("SMS send failed: category={}, retryable={}, message={}",
                    e.getCategory(), e.isRetryable(), e.getMessage());
            return ProviderSendResponse.of(
                    false,
                    null,
                    FailureDetails.of(e.getCategory(), "PROVIDER_ERROR", e.getMessage(), e.isRetryable()),
                    Instant.now().toEpochMilli()
            );
        } catch (Exception e) {
            log.error("Unexpected SMS send error", e);
            return ProviderSendResponse.of(
                    false,
                    null,
                    FailureDetails.of(FailureCategory.UNKNOWN, "UNEXPECTED_ERROR", e.getMessage(), true),
                    Instant.now().toEpochMilli()
            );
        }
    }

    private String sendViaProvider(String recipient, String message, String idempotencyKey) {
        // Provider-specific implementation
        return switch (providerType.toLowerCase()) {
            case "twilio" -> sendViaTwilio(recipient, message, idempotencyKey);
            case "http" -> sendViaHttp(recipient, message, idempotencyKey);
            default -> throw new NotificationProviderException(
                    "Unknown SMS provider type: " + providerType,
                    FailureCategory.PROVIDER_UNAVAILABLE,
                    false
            );
        };
    }

    private String sendViaTwilio(String recipient, String message, String idempotencyKey) {
        // TODO: Implement Twilio API call when credentials provided
        // Example:
        // Twilio.init(twilioAccountSid, twilioAuthToken);
        // MessageCreator creator = Message.creator(
        //     new PhoneNumber(recipient),
        //     new PhoneNumber(twilioFromNumber),
        //     message
        // );
        // if (idempotencyKey != null) {
        //     creator.setProvideFeedback(true);
        // }
        // Message message = creator.create();
        // return message.getSid();

        throw new NotificationProviderException(
                "Twilio provider not implemented - configure and implement sendViaTwilio()",
                FailureCategory.PROVIDER_UNAVAILABLE,
                false
        );
    }

    private String sendViaHttp(String recipient, String message, String idempotencyKey) {
        // TODO: Implement generic HTTP provider call when configured
        // Example using RestTemplate or WebClient:
        // HttpHeaders headers = new HttpHeaders();
        // headers.setBearerAuth(httpApiKey);
        // headers.setContentType(MediaType.APPLICATION_JSON);
        // Map<String, Object> body = Map.of(
        //     "to", recipient,
        //     "from", httpFrom,
        //     "text", message
        // );
        // if (idempotencyKey != null) body.put("idempotency_key", idempotencyKey);
        // ResponseEntity<String> response = restTemplate.postForEntity(httpUrl, new HttpEntity<>(body, headers), String.class);
        // Parse response for provider message ID

        throw new NotificationProviderException(
                "HTTP SMS provider not implemented - configure and implement sendViaHttp()",
                FailureCategory.PROVIDER_UNAVAILABLE,
                false
        );
    }

    private boolean validateConfiguration() {
        return switch (providerType.toLowerCase()) {
            case "twilio" -> !twilioAccountSid.isBlank() && !twilioAuthToken.isBlank() && !twilioFromNumber.isBlank();
            case "http" -> !httpUrl.isBlank() && !httpApiKey.isBlank() && !httpFrom.isBlank();
            default -> false;
        };
    }

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public long getBaseBackoffMs() {
        return baseBackoffMs;
    }

    @Override
    public boolean isRetryableFailure(ProviderSendResponse response) {
        return response.failureDetails() != null && response.failureDetails().retryable();
    }

    @Override
    public boolean supportsType(NotificationType type) {
        return type == NotificationType.SERVICE_REMINDER
                || type == NotificationType.APPOINTMENT_CONFIRMATION
                || type == NotificationType.APPOINTMENT_CONFIRMATION
                || type == NotificationType.JOB_STATUS_UPDATE
                || type == NotificationType.INVOICE_READY
                || type == NotificationType.PAYMENT_RECEIPT
                || type == NotificationType.GENERAL_ALERT;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 1);
    }
}