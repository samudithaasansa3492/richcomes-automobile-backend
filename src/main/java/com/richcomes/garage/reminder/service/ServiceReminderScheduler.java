package com.richcomes.garage.reminder.service;

import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ServiceReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ServiceReminderScheduler.class);
    private final ServiceReminderService reminderService;
    private final ObjectProvider<CustomerPhoneResolver> phoneResolver;

    public ServiceReminderScheduler(ServiceReminderService reminderService,
                                    ObjectProvider<CustomerPhoneResolver> phoneResolver) {
        this.reminderService = reminderService;
        this.phoneResolver = phoneResolver;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Colombo")
    public void checkDueReminders() {
        CustomerPhoneResolver resolver = phoneResolver.getIfAvailable();
        if (resolver == null) {
            log.warn("Reminder processing skipped: implement CustomerPhoneResolver using CustomerService");
            return;
        }
        for (ServiceReminderResponse reminder : reminderService.getDueReminders()) {
            try {
                String phone = resolver.phoneForCustomer(reminder.getCustomerId());
                if (phone == null || phone.isBlank()) {
                    log.warn("No verified phone for reminder {}", reminder.getId());
                    continue;
                }
                reminderService.sendReminder(reminder.getId(), phone);
            } catch (Exception e) {
                log.error("Could not queue reminder {}: {}", reminder.getId(), e.getMessage(), e);
            }
        }
    }
}
