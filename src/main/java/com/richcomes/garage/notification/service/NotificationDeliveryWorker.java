package com.richcomes.garage.notification.service;

import com.richcomes.garage.notification.entity.Notification;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.notification.provider.NotificationProvider;
import com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendRequest;
import com.richcomes.garage.notification.provider.NotificationProvider.ProviderSendResponse;
import com.richcomes.garage.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background worker that processes PENDING notifications and delivers them via configured providers.
 *
 * Features:
 * - Processes notifications in batches
 * - Uses SELECT FOR UPDATE to prevent concurrent processing
 * - Implements retry with exponential backoff
 * - Handles provider rate limits and temporary failures
 * - Prevents duplicate sends via provider idempotency keys
 * - Safe restart: interrupted notifications return to PENDING for retry
 *
 * Safety:
 * - Only processes PENDING notifications with retry count < max retries
 * - Never processes FAILED notifications (manual retry only)
 * - Fails closed: if provider unavailable, leaves notifications for next run
 * - Logs without exposing sensitive data
 */
@Component
public class NotificationDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationRepository notificationRepository;
    private final List<NotificationProvider> providers;
    private final NotificationService notificationService;

    // Configuration
    private final int batchSize;
    private final int maxConcurrentWorkers;

    // Metrics
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);

    public NotificationDeliveryWorker(
            NotificationRepository notificationRepository,
            List<NotificationProvider> providers,
            NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Value("${notification.worker.batch-size:50}") int batchSize,
            @org.springframework.beans.factory.annotation.Value("${notification.worker.max-concurrent:1}") int maxConcurrentWorkers) {
        this.notificationRepository = notificationRepository;
        this.providers = providers;
        this.notificationService = notificationService;
        this.batchSize = Math.max(1, batchSize);
        this.maxConcurrentWorkers = Math.max(1, maxConcurrentWorkers);
    }

    /**
     * Process pending notifications.
     * Runs every 30 seconds by default.
     * Only one instance runs at a time (enforced by @Scheduled single-threaded).
     */
    @Scheduled(fixedDelayString = "${notification.worker.interval-ms:30000}")
    @Transactional
    public void processPendingNotifications() {
        if (providers.isEmpty()) {
            log.debug("No notification providers configured - skipping processing");
            return;
        }

        log.debug("Starting notification delivery batch processing");

        // Get enabled providers
        List<NotificationProvider> enabledProviders = providers.stream()
                .filter(NotificationProvider::isEnabled)
                .toList();

        if (enabledProviders.isEmpty()) {
            log.debug("No enabled notification providers - skipping processing");
            return;
        }

        // Fetch and lock pending notifications
        List<Notification> pending = fetchAndLockPending(enabledProviders);

        if (pending.isEmpty()) {
            log.debug("No pending notifications to process");
            return;
        }

        log.info("Processing {} pending notifications", pending.size());

        int batchProcessed = 0;
        int batchFailed = 0;
        int batchSkipped = 0;

        for (Notification notification : pending) {
            try {
                boolean processed = processNotification(notification, enabledProviders);
                if (processed) {
                    batchProcessed++;
                    processedCount.incrementAndGet();
                } else {
                    batchSkipped++;
                    skippedCount.incrementAndGet();
                }
            } catch (Exception e) {
                batchFailed++;
                failedCount.incrementAndGet();
                log.error("Unexpected error processing notification {}: {}",
                        notification.getId(), e.getMessage(), e);
                // Don't mark as failed here - let it retry naturally
            }
        }

        log.info("Batch complete: processed={}, skipped={}, failed={} (totals: processed={}, failed={}, skipped={})",
                batchProcessed, batchSkipped, batchFailed,
                processedCount.get(), failedCount.get(), skippedCount.get());
    }

    /**
     * Fetch pending notifications and lock them for processing.
     * Only picks up notifications that:
     * - Status = PENDING
     * - Retry count < provider max retries
     * - Next retry time <= now (for retries)
     * - Provider is enabled for this channel
     */
    private List<Notification> fetchAndLockPending(List<NotificationProvider> enabledProviders) {
        // Get all enabled channels
        var enabledChannels = enabledProviders.stream()
                .map(NotificationProvider::getChannel)
                .toList();

        // Use repository method with locking
        return notificationRepository.findPendingForProcessing(
                enabledChannels,
                Instant.now(),
                org.springframework.data.domain.PageRequest.of(0, batchSize)
        );
    }

    /**
     * Process a single notification through its channel's provider.
     * @return true if processed (sent or permanently failed), false if skipped for retry
     */
    private boolean processNotification(Notification notification, List<NotificationProvider> enabledProviders) {
        // Find provider for this channel
        NotificationProvider provider = enabledProviders.stream()
                .filter(p -> p.getChannel() == notification.getChannel())
                .findFirst()
                .orElse(null);

        if (provider == null) {
            log.warn("No provider for channel {} - skipping notification {}",
                    notification.getChannel(), notification.getId());
            return false;
        }

        // Check retry limits
        int maxRetries = provider.getMaxRetries();
        if (notification.getRetryCount() > maxRetries) {
            log.warn("Notification {} exceeded max retries ({}) - marking permanent failure",
                    notification.getId(), maxRetries);
            markPermanentFailure(notification, "Exceeded maximum retry attempts (" + maxRetries + ")");
            return true;
        }

        // Check if it's time to retry (for retried notifications)
        if (notification.getRetryCount() > 0 && notification.getNextRetryAt() != null) {
            if (notification.getNextRetryAt().isAfter(Instant.now())) {
                log.debug("Notification {} not yet due for retry (next: {})",
                        notification.getId(), notification.getNextRetryAt());
                return false; // Skip this batch, will pick up next time
            }
        }

        // Check provider supports this notification type
        if (notification.getType() != null && !provider.supportsType(notification.getType())) {
            log.warn("Provider for channel {} does not support type {} - notification {}",
                    notification.getChannel(), notification.getType(), notification.getId());
            markPermanentFailure(notification, "Notification type not supported by provider");
            return true;
        }

        // Build send request
        ProviderSendRequest request = buildSendRequest(notification);

        // Attempt delivery
        ProviderSendResponse response = provider.send(request);

        if (response.accepted()) {
            // Success!
            markSent(notification, response.providerMessageId());
            return true;
        } else {
            // Failure - classify and handle
            return handleFailure(notification, provider, response);
        }
    }

    private ProviderSendRequest buildSendRequest(Notification notification) {
        String idempotencyKey = "notif-" + notification.getId() + "-retry-" + notification.getRetryCount();
        String callbackUrl = buildCallbackUrl(notification.getId());

        return new ProviderSendRequest(
                notification.getRecipient(),
                notification.getMessage(),
                notification.getType(),
                Optional.of(idempotencyKey),
                Optional.of(callbackUrl)
        );
    }

    private String buildCallbackUrl(Long notificationId) {
        // TODO: Configure base URL from properties
        return "http://localhost:8080/api/v1/notifications/callback/" + notificationId;
    }

    private boolean handleFailure(Notification notification, NotificationProvider provider, ProviderSendResponse response) {
        var failure = response.failureDetails();

        if (failure == null) {
            log.warn("Notification {} failed without failure details - treating as retryable",
                    notification.getId());
            scheduleRetry(notification, provider, true);
            return true;
        }

        if (failure.retryable()) {
            log.info("Notification {} retryable failure (category: {}) - scheduling retry {} of {}",
                    notification.getId(), failure.category(), notification.getRetryCount() + 1, provider.getMaxRetries());
            scheduleRetry(notification, provider, true);
            return true;
        } else {
            log.warn("Notification {} permanent failure (category: {}) - marking failed",
                    notification.getId(), failure.category());
            markPermanentFailure(notification, failure.providerErrorMessage() != null
                    ? failure.providerErrorMessage()
                    : "Permanent failure: " + failure.category());
            return true;
        }
    }

    private void scheduleRetry(Notification notification, NotificationProvider provider, boolean retryable) {
        int nextRetryCount = notification.getRetryCount() + 1;

        // Exponential backoff: base * 2^retryCount
        long backoffMs = provider.getBaseBackoffMs() * (1L << Math.min(nextRetryCount - 1, 10)); // Cap at 2^10

        // Add jitter (±25%)
        long jitter = (long) (backoffMs * 0.25 * (Math.random() * 2 - 1));
        long nextRetryMs = backoffMs + jitter;

        Instant nextRetryAt = Instant.now().plusMillis(nextRetryMs);

        notification.setRetryCount(nextRetryCount);
        notification.setNextRetryAt(nextRetryAt);
        notification.setFailureReason("Retry " + nextRetryCount + ": " +
                (retryable ? "retryable" : "permanent") + " failure");
        notification.setStatus(NotificationStatus.PENDING); // Keep as PENDING for retry
        notification.setUpdatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    private void markSent(Notification notification, String providerMessageId) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notification.setProviderMessageId(providerMessageId);
        notification.setFailureReason(null);
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        log.info("Notification {} marked SENT (provider: {})",
                notification.getId(), providerMessageId);
    }

    private void markPermanentFailure(Notification notification, String reason) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setFailureReason(reason);
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        log.warn("Notification {} marked FAILED: {}", notification.getId(), reason);
    }

    // =====================================================
    // METRICS / HEALTH
    // =====================================================

    public long getProcessedCount() { return processedCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
    public long getSkippedCount() { return skippedCount.get(); }
}