package com.richcomes.garage.reminder.service;

import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task to process due service reminders.
 *
 * Runs daily at 9:00 AM Asia/Colombo time.
 * Processes reminders that are:
 * - Status = PENDING
 * - reminder_date <= today
 * - notification_id IS NULL (not yet queued)
 *
 * For each due reminder:
 * 1. Resolves customer phone via CustomerPhoneResolver
 * 2. Validates phone format and consent (via resolver)
 * 3. Queues notification via ServiceReminderService.sendReminder()
 * 4. Logs success/failure for audit
 *
 * Safety guarantees:
 * - Never sends notification without resolved phone
 * - Never processes reminders already queued (notification_id check)
 * - Continues processing other reminders if one fails
 * - Fails closed: skips reminder if phone unavailable or invalid
 */
@Component
public class ServiceReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ServiceReminderScheduler.class);
    private final ServiceReminderService reminderService;
    private final CustomerPhoneResolver phoneResolver;

    public ServiceReminderScheduler(ServiceReminderService reminderService,
                                    CustomerPhoneResolver phoneResolver) {
        this.reminderService = reminderService;
        this.phoneResolver = phoneResolver;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Colombo")
    public void checkDueReminders() {
        log.info("Starting scheduled reminder processing");

        if (phoneResolver == null) {
            log.error("CustomerPhoneResolver not available - reminder processing aborted");
            return;
        }

        List<ServiceReminderResponse> dueReminders = reminderService.getDueReminders();
        log.info("Found {} due reminders to process", dueReminders.size());

        int processed = 0;
        int skippedNoPhone = 0;
        int skippedError = 0;

        for (ServiceReminderResponse reminder : dueReminders) {
            try {
                // Double-check: only process PENDING reminders without notification
                if (reminder.getStatus() != ServiceReminderStatus.PENDING) {
                    log.debug("Skipping reminder {} - status is {}", reminder.getId(), reminder.getStatus());
                    continue;
                }
                if (reminder.getNotificationId() != null) {
                    log.debug("Skipping reminder {} - notification already queued ({})", reminder.getId(), reminder.getNotificationId());
                    continue;
                }

                String phone = phoneResolver.phoneForCustomer(reminder.getCustomerId());

                if (phone == null || phone.isBlank()) {
                    log.warn("Reminder {} skipped: no verified phone for customer {}",
                            reminder.getId(), reminder.getCustomerId());
                    skippedNoPhone++;
                    continue;
                }

                // Queue the notification - sendReminder handles duplicate prevention
                reminderService.sendReminder(reminder.getId(), phone);
                processed++;
                log.info("Reminder {} queued for customer {} (phone: {})",
                        reminder.getId(), reminder.getCustomerId(), maskPhone(phone));

            } catch (Exception e) {
                skippedError++;
                log.error("Failed to process reminder {}: {}",
                        reminder.getId(), e.getMessage(), e);
            }
        }

        log.info("Reminder processing complete: processed={}, skippedNoPhone={}, errors={}",
                processed, skippedNoPhone, skippedError);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 1);
    }
}
