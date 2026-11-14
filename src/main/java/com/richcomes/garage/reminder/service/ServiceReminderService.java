
package com.richcomes.garage.reminder.service;

import com.richcomes.garage.history.entity.ServiceHistory;
import com.richcomes.garage.history.repository.ServiceHistoryRepository;

import com.richcomes.garage.reminder.dto.GenerateReminderRequest;
import com.richcomes.garage.reminder.dto.ServiceReminderCreateRequest;
import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import com.richcomes.garage.reminder.dto.ServiceReminderUpdateRequest;
import com.richcomes.garage.reminder.entity.ServiceReminder;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import com.richcomes.garage.reminder.repository.ServiceReminderRepository;

import com.richcomes.garage.notification.service.NotificationService;
import com.richcomes.garage.notification.dto.NotificationSendRequest;
import com.richcomes.garage.notification.dto.NotificationResponse;
import com.richcomes.garage.notification.enums.NotificationType;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class ServiceReminderService {

    private final ServiceReminderRepository reminderRepository;
    private final ServiceHistoryRepository serviceHistoryRepository;
    private final NotificationService notificationService;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private LocalDate today() { return LocalDate.now(BUSINESS_ZONE); }

    public ServiceReminderService(
            ServiceReminderRepository reminderRepository,
            ServiceHistoryRepository serviceHistoryRepository,
            NotificationService notificationService) {

        this.reminderRepository = reminderRepository;
        this.serviceHistoryRepository = serviceHistoryRepository;
        this.notificationService = notificationService;
    }

    // CREATE REMINDER
    public ServiceReminderResponse createReminder(
            ServiceReminderCreateRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder request is required"
            );
        }

        if (request.getVehicleId() == null ||
                request.getCustomerId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle ID and customer ID are required"
            );
        }

        if (request.getReminderDate() == null ||
                request.getNextServiceDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder date and next service date are required"
            );
        }

        if (!request.getReminderDate()
                .equals(request.getNextServiceDate().minusDays(7))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder date must be exactly 7 days before next service date"
            );
        }

        if (request.getMessage() == null ||
                request.getMessage().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder message cannot be blank"
            );
        }

        if (request.getServiceHistoryId() != null) {
            ServiceHistory history = findHistoryById(
                    request.getServiceHistoryId()
            );

            if (!history.getVehicleId()
                    .equals(request.getVehicleId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vehicle does not match service history"
                );
            }

            checkDuplicateHistoryReminder(history.getId());
        }

        ServiceReminder reminder = new ServiceReminder();

        reminder.setVehicleId(request.getVehicleId());
        reminder.setCustomerId(request.getCustomerId());
        reminder.setServiceHistoryId(request.getServiceHistoryId());
        reminder.setNextServiceDate(request.getNextServiceDate());
        reminder.setReminderDate(request.getReminderDate());
        reminder.setMessage(request.getMessage().trim());
        reminder.setAiSuggestedDate(request.getAiSuggestedDate());
        reminder.setStatus(ServiceReminderStatus.PENDING);

        return mapToResponse(reminderRepository.save(reminder));
    }

    // GENERATE REMINDER FROM SERVICE HISTORY
    public ServiceReminderResponse generateFromServiceHistory(
            GenerateReminderRequest request) {

        if (request == null ||
                request.getServiceHistoryId() == null ||
                request.getCustomerId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service history ID and customer ID are required"
            );
        }

        ServiceHistory history =
                findHistoryById(request.getServiceHistoryId());

        checkDuplicateHistoryReminder(history.getId());

        LocalDate nextServiceDate = history.getNextServiceDate();

        if (nextServiceDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Next service date is missing in service history"
            );
        }

        if (nextServiceDate.isBefore(today())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Next service date has already passed"
            );
        }

        LocalDate reminderDate = nextServiceDate.minusDays(7);

        if (reminderDate.isBefore(today())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot schedule a reminder exactly 7 days before service: "
                            + "the reminder date has already passed"
            );
        }

        String message = String.format(
                "Dear Customer, your vehicle is scheduled " +
                "for periodic service on %s. Previous service " +
                "at %d km was completed on %s. " +
                "Please book an appointment with Richcomes Automobile.",
                nextServiceDate,
                history.getOdometer(),
                history.getServiceDate()
        );

        ServiceReminder reminder = new ServiceReminder();

        reminder.setVehicleId(history.getVehicleId());
        reminder.setCustomerId(request.getCustomerId());
        reminder.setServiceHistoryId(history.getId());
        if (reminder.getNotificationId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot edit a reminder after its notification is queued");
        }
        reminder.setNextServiceDate(nextServiceDate);
        reminder.setReminderDate(reminderDate);
        reminder.setMessage(message);
        reminder.setAiSuggestedDate(null);
        reminder.setStatus(ServiceReminderStatus.PENDING);

        return mapToResponse(reminderRepository.save(reminder));
    }

    // GET ALL REMINDERS
    @Transactional(readOnly = true)
    public List<ServiceReminderResponse> getAllReminders(
            ServiceReminderStatus status) {

        List<ServiceReminder> reminders;

        if (status != null) {
            reminders =
                    reminderRepository.findByStatusOrderByReminderDateAsc(
                            status
                    );
        } else {
            reminders = reminderRepository.findAll();
        }

        return reminders.stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET REMINDER BY ID
    @Transactional(readOnly = true)
    public ServiceReminderResponse getReminderById(Long id) {
        return mapToResponse(findReminderById(id));
    }

    // GET REMINDERS BY VEHICLE
    @Transactional(readOnly = true)
    public List<ServiceReminderResponse> getRemindersByVehicle(
            Long vehicleId) {

        return reminderRepository
                .findByVehicleIdOrderByNextServiceDateAsc(vehicleId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET REMINDERS BY CUSTOMER
    @Transactional(readOnly = true)
    public List<ServiceReminderResponse> getRemindersByCustomer(
            Long customerId) {

        return reminderRepository
                .findByCustomerIdOrderByReminderDateAsc(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET DUE REMINDERS
    @Transactional(readOnly = true)
    public List<ServiceReminderResponse> getDueReminders() {

        return reminderRepository
                .findByStatusAndNotificationIdIsNullAndReminderDateLessThanEqual(
                        ServiceReminderStatus.PENDING,
                        today()
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // QUEUE NOTIFICATION FOR REMINDER
    // This does not confirm SMS delivery.
    public ServiceReminderResponse sendReminder(
            Long id,
            String recipient) {

        ServiceReminder reminder = reminderRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reminder not found"));

        if (reminder.getNotificationId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A notification has already been queued for this reminder"
            );
        }

        if (reminder.getStatus() != ServiceReminderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending reminders can be sent"
            );
        }

        if (reminder.getReminderDate().isAfter(today())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reminder is not due yet");
        }

        if (recipient == null || recipient.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer phone number is required"
            );
        }

        NotificationSendRequest request =
                new NotificationSendRequest();

        request.setCustomerId(reminder.getCustomerId());
        request.setVehicleId(reminder.getVehicleId());
        request.setType(NotificationType.SERVICE_REMINDER);
        request.setChannel(NotificationChannel.SMS);
        request.setRecipient(recipient.trim());
        request.setMessage(reminder.getMessage());

        NotificationResponse notification =
                notificationService.sendNotification(request);

        if (notification == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Notification service returned no response"
            );
        }

        if (notification.getStatus() == NotificationStatus.FAILED) {
            // A failed validation is recorded by NotificationService.
            // Keep this reminder eligible for a corrected phone number.
            return mapToResponse(reminder);
        }

        // Store the queued notification ID to avoid re-queueing each day.
        // This does NOT mean the SMS was delivered.
        reminder.setNotificationId(notification.getId());
        return mapToResponse(reminderRepository.save(reminder));
    }

    // CANCEL REMINDER
    public ServiceReminderResponse cancelReminder(Long id) {

        ServiceReminder reminder = findReminderById(id);

        if (reminder.getStatus() != ServiceReminderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending reminders can be cancelled"
            );
        }

        if (reminder.getNotificationId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Notification already queued; cancel it through the Notification module first");
        }
        reminder.setStatus(ServiceReminderStatus.CANCELLED);

        return mapToResponse(reminderRepository.save(reminder));
    }

    // UPDATE REMINDER
    public ServiceReminderResponse updateReminder(
            Long id,
            ServiceReminderUpdateRequest request) {

        ServiceReminder reminder = findReminderById(id);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Update request is required"
            );
        }

        if (reminder.getStatus() != ServiceReminderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending reminders can be updated"
            );
        }

        LocalDate nextServiceDate =
                request.getNextServiceDate() != null
                        ? request.getNextServiceDate()
                        : reminder.getNextServiceDate();

        LocalDate reminderDate =
                request.getReminderDate() != null
                        ? request.getReminderDate()
                        : reminder.getReminderDate();

        if (nextServiceDate == null || reminderDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service date and reminder date are required"
            );
        }

        if (!reminderDate.equals(nextServiceDate.minusDays(7))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder date must be exactly 7 days before next service date"
            );
        }

        if (request.getMessage() != null &&
                request.getMessage().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder message cannot be blank"
            );
        }

        if (reminder.getNotificationId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot edit a reminder after its notification is queued");
        }
        reminder.setNextServiceDate(nextServiceDate);
        reminder.setReminderDate(reminderDate);

        if (request.getMessage() != null) {
            reminder.setMessage(request.getMessage().trim());
        }

        if (request.getAiSuggestedDate() != null) {
            reminder.setAiSuggestedDate(
                    request.getAiSuggestedDate()
            );
        }

        return mapToResponse(reminderRepository.save(reminder));
    }

    // PROTECT REMINDER HISTORY
    public void deleteReminder(Long id) {

        findReminderById(id);

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Reminder records cannot be deleted. " +
                "Use the cancel endpoint instead."
        );
    }

    // FIND HISTORY
    private ServiceHistory findHistoryById(Long id) {

        return serviceHistoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Service history ID " + id + " not found"
                ));
    }

    // PREVENT DUPLICATE HISTORY REMINDERS
    private void checkDuplicateHistoryReminder(Long historyId) {

        if (!reminderRepository
                .findByServiceHistoryId(historyId)
                .isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A reminder already exists for this service history"
            );
        }
    }

    // FIND REMINDER
    private ServiceReminder findReminderById(Long id) {

        return reminderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Service reminder ID " + id + " not found"
                ));
    }

    // ENTITY TO RESPONSE
    public ServiceReminderResponse mapToResponse(
            ServiceReminder reminder) {

        ServiceReminderResponse response =
                new ServiceReminderResponse();

        response.setId(reminder.getId());
        response.setVehicleId(reminder.getVehicleId());
        response.setCustomerId(reminder.getCustomerId());
        response.setServiceHistoryId(reminder.getServiceHistoryId());
        response.setNotificationId(reminder.getNotificationId());
        response.setNextServiceDate(reminder.getNextServiceDate());
        response.setReminderDate(reminder.getReminderDate());
        response.setStatus(reminder.getStatus());
        response.setMessage(reminder.getMessage());
        response.setAiSuggestedDate(reminder.getAiSuggestedDate());
        response.setCreatedAt(reminder.getCreatedAt());
        response.setUpdatedAt(reminder.getUpdatedAt());

        return response;
    }
}
