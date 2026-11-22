package com.richcomes.garage.reminder.service;

import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ServiceReminderSchedulerTest {

    @Mock
    private ServiceReminderService reminderService;

    @Mock
    private CustomerPhoneResolver phoneResolver;

    @InjectMocks
    private ServiceReminderScheduler scheduler;

    private ServiceReminderResponse dueReminder;

    @BeforeEach
    void setUp() {
        dueReminder = new ServiceReminderResponse();
        dueReminder.setId(1L);
        dueReminder.setCustomerId(1L);
        dueReminder.setVehicleId(10L);
        dueReminder.setStatus(ServiceReminderStatus.PENDING);
    }

    @Test
    void checkDueReminders_withNoPhoneResolver_skipsProcessingAndLogsError() {
        // Create scheduler with null resolver
        ServiceReminderScheduler nullResolverScheduler = new ServiceReminderScheduler(reminderService, null);

        nullResolverScheduler.checkDueReminders();

        verify(reminderService, never()).getDueReminders();
    }

    @Test
    void checkDueReminders_withPhoneResolverAndNoDueReminders_doesNothing() {
        when(reminderService.getDueReminders()).thenReturn(List.of());

        scheduler.checkDueReminders();

        verify(reminderService).getDueReminders();
        verify(phoneResolver, never()).phoneForCustomer(anyLong());
    }

    @Test
    void checkDueReminders_withPhoneResolverAndDueReminders_processesEach() {
        when(reminderService.getDueReminders()).thenReturn(List.of(dueReminder));
        when(phoneResolver.phoneForCustomer(1L)).thenReturn("+94771234567");

        scheduler.checkDueReminders();

        verify(reminderService).getDueReminders();
        verify(phoneResolver).phoneForCustomer(1L);
        verify(reminderService).sendReminder(1L, "+94771234567");
    }

    @Test
    void checkDueReminders_withNullPhone_skipsThatReminder() {
        when(reminderService.getDueReminders()).thenReturn(List.of(dueReminder));
        when(phoneResolver.phoneForCustomer(1L)).thenReturn(null);

        scheduler.checkDueReminders();

        verify(reminderService).getDueReminders();
        verify(phoneResolver).phoneForCustomer(1L);
        verify(reminderService, never()).sendReminder(anyLong(), anyString());
    }

    @Test
    void checkDueReminders_withBlankPhone_skipsThatReminder() {
        when(reminderService.getDueReminders()).thenReturn(List.of(dueReminder));
        when(phoneResolver.phoneForCustomer(1L)).thenReturn("   ");

        scheduler.checkDueReminders();

        verify(reminderService, never()).sendReminder(anyLong(), anyString());
    }

    @Test
    void checkDueReminders_withSendReminderException_logsErrorAndContinues() {
        ServiceReminderResponse secondReminder = new ServiceReminderResponse();
        secondReminder.setId(2L);
        secondReminder.setCustomerId(2L);
        secondReminder.setVehicleId(20L);
        secondReminder.setStatus(ServiceReminderStatus.PENDING);

        when(reminderService.getDueReminders()).thenReturn(List.of(dueReminder, secondReminder));
        when(phoneResolver.phoneForCustomer(1L)).thenReturn("+94771234567");
        when(phoneResolver.phoneForCustomer(2L)).thenReturn("+94771234568");
        doThrow(new RuntimeException("SMS gateway error")).when(reminderService).sendReminder(1L, "+94771234567");

        // Should not throw, just log error
        scheduler.checkDueReminders();

        verify(reminderService).sendReminder(1L, "+94771234567");
        verify(reminderService).sendReminder(2L, "+94771234568");
    }
}