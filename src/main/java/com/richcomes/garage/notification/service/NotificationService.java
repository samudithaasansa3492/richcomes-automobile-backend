
package com.richcomes.garage.notification.service;

import com.richcomes.garage.notification.dto.NotificationResponse;
import com.richcomes.garage.notification.dto.NotificationSendRequest;
import com.richcomes.garage.notification.entity.Notification;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.notification.repository.NotificationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // CREATE AND QUEUE NOTIFICATION
    // PENDING means queued, not delivered.
    public NotificationResponse sendNotification(
            NotificationSendRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification request is required"
            );
        }

        if (request.getType() == null ||
                request.getChannel() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification type and channel are required"
            );
        }

        if (request.getMessage() == null ||
                request.getMessage().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification message cannot be blank"
            );
        }

        if (request.getMessage().length() > 5000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification message cannot exceed 5000 characters"
            );
        }

        if (request.getRecipient() == null ||
                request.getRecipient().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recipient is required"
            );
        }

        String recipient = request.getRecipient().trim();

        if (recipient.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recipient cannot exceed 150 characters"
            );
        }

        Notification notification = new Notification();

        notification.setCustomerId(request.getCustomerId());
        notification.setVehicleId(request.getVehicleId());
        notification.setAppointmentId(request.getAppointmentId());
        notification.setType(request.getType());
        notification.setChannel(request.getChannel());
        notification.setRecipient(recipient);
        notification.setMessage(request.getMessage().trim());

        if (validateRecipient(request.getChannel(), recipient)) {
            notification.setStatus(NotificationStatus.PENDING);
            notification.setSentAt(null);
            notification.setFailureReason(null);
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setSentAt(null);
            notification.setFailureReason(
                    "Invalid recipient address format for channel "
                            + request.getChannel()
            );
        }

        return mapToResponse(
                notificationRepository.save(notification)
        );
    }

    // GET ALL NOTIFICATIONS WITH FILTERS AND PAGINATION
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getAllNotifications(
            NotificationStatus status,
            NotificationChannel channel,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be 1 to 100"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<Notification> notifications;

        if (status != null && channel != null) {
            notifications = notificationRepository
                    .findByStatusAndChannelOrderByCreatedAtDescIdDesc(
                            status, channel, pageable
                    );
        } else if (status != null) {
            notifications = notificationRepository
                    .findByStatusOrderByCreatedAtDescIdDesc(
                            status, pageable
                    );
        } else if (channel != null) {
            notifications = notificationRepository
                    .findByChannelOrderByCreatedAtDescIdDesc(
                            channel, pageable
                    );
        } else {
            notifications = notificationRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);
        }

        return notifications.map(this::mapToResponse);
    }

    // GET NOTIFICATION BY ID
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long id) {
        return mapToResponse(findNotificationById(id));
    }

    // GET CUSTOMER NOTIFICATIONS
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByCustomer(
            Long customerId) {

        return notificationRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET VEHICLE NOTIFICATIONS
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByVehicle(
            Long vehicleId) {

        return notificationRepository
                .findByVehicleIdOrderByCreatedAtDesc(vehicleId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // RETRY FAILED NOTIFICATION
    // This requeues the record; it does not deliver the SMS.
    public NotificationResponse retryNotification(Long id) {

        Notification notification = findNotificationById(id);

        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only failed notifications can be retried"
            );
        }

        boolean valid = validateRecipient(
                notification.getChannel(),
                notification.getRecipient()
        );

        if (valid) {
            notification.setStatus(NotificationStatus.PENDING);
            notification.setSentAt(null);
            notification.setFailureReason(null);
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setSentAt(null);
            notification.setFailureReason(
                    "Retry failed: Invalid recipient address format"
            );
        }

        return mapToResponse(
                notificationRepository.save(notification)
        );
    }

    // PRESERVE NOTIFICATION HISTORY
    public void deleteNotification(Long id) {

        findNotificationById(id);

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Notification records cannot be deleted because "
                        + "they are needed for audit and reporting"
        );
    }

    // VALIDATE RECIPIENT BASED ON CHANNEL
    private boolean validateRecipient(
            NotificationChannel channel,
            String recipient) {

        if (channel == null ||
                recipient == null ||
                recipient.isBlank()) {
            return false;
        }

        String clean = recipient.trim();

        return switch (channel) {
            case SMS -> clean.matches(
                    "^(?:\\+94|0)7[0-9]{8}$"
            );

            case EMAIL -> clean.matches(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            );

            case SYSTEM -> true;
        };
    }

    // FIND NOTIFICATION
    private Notification findNotificationById(Long id) {

        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification with ID " + id + " not found"
                ));
    }

    // ENTITY TO RESPONSE DTO
    public NotificationResponse mapToResponse(
            Notification notification) {

        NotificationResponse response = new NotificationResponse();

        response.setId(notification.getId());
        response.setCustomerId(notification.getCustomerId());
        response.setVehicleId(notification.getVehicleId());
        response.setAppointmentId(notification.getAppointmentId());
        response.setType(notification.getType());
        response.setChannel(notification.getChannel());
        response.setRecipient(notification.getRecipient());
        response.setMessage(notification.getMessage());
        response.setStatus(notification.getStatus());
        response.setSentAt(notification.getSentAt());
        response.setFailureReason(notification.getFailureReason());
        response.setCreatedAt(notification.getCreatedAt());

        return response;
    }
}
