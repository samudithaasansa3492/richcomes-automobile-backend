package com.richcomes.garage.notification.service;

import com.richcomes.garage.notification.dto.NotificationResponse;
import com.richcomes.garage.notification.dto.NotificationSendRequest;
import com.richcomes.garage.notification.entity.Notification;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.notification.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Sends a notification across specified channel (SMS, EMAIL, SYSTEM).
     * Tracks delivery status and timestamp.
     */
    public NotificationResponse sendNotification(NotificationSendRequest request) {
        Notification notification = new Notification();
        notification.setCustomerId(request.getCustomerId());
        notification.setVehicleId(request.getVehicleId());
        notification.setAppointmentId(request.getAppointmentId());
        notification.setType(request.getType());
        notification.setChannel(request.getChannel());
        notification.setRecipient(request.getRecipient().trim());
        notification.setMessage(request.getMessage().trim());

        // Validate recipient based on channel

        boolean isValid = validateRecipient(
                request.getChannel(),
                request.getRecipient()
        );

        if (isValid) {
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

        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getAllNotifications(
            NotificationStatus status,
            NotificationChannel channel,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Notification> notifications;

        if (status != null && channel != null) {
            notifications = notificationRepository
                    .findByStatusAndChannelOrderByCreatedAtDescIdDesc(
                            status, channel, pageable);

        } else if (status != null) {
            notifications = notificationRepository
                    .findByStatusOrderByCreatedAtDescIdDesc(
                            status, pageable);

        } else if (channel != null) {
            notifications = notificationRepository
                    .findByChannelOrderByCreatedAtDescIdDesc(
                            channel, pageable);

        } else {
            notifications = notificationRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);
        }

        return notifications.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long id) {
        Notification notification = findNotificationById(id);
        return mapToResponse(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByCustomer(Long customerId) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByVehicle(Long vehicleId) {
        return notificationRepository.findByVehicleIdOrderByCreatedAtDesc(vehicleId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public NotificationResponse retryNotification(Long id) {
        Notification notification = findNotificationById(id);

        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only failed notifications can be retried"
            );
        }

        boolean isValid = validateRecipient(
                notification.getChannel(),
                notification.getRecipient()
        );

        if (isValid) {
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

        Notification updated = notificationRepository.save(notification);
        return mapToResponse(updated);
    }

    public void deleteNotification(Long id) {
        Notification notification = findNotificationById(id);
        notificationRepository.delete(notification);
    }

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

    private Notification findNotificationById(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification with ID " + id + " not found"
                ));
    }

    public NotificationResponse mapToResponse(Notification notification) {
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
