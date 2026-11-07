package com.richcomes.garage.notification.controller;

import com.richcomes.garage.notification.dto.NotificationResponse;
import com.richcomes.garage.notification.dto.NotificationSendRequest;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendNotification(
            @Valid @RequestBody NotificationSendRequest request) {
        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/test")
    public ResponseEntity<String> testApi() {
        return ResponseEntity.ok("Notification API is working!");
    }
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getAllNotifications(
            @RequestParam(required = false)
            NotificationStatus status,

            @RequestParam(required = false)
            NotificationChannel channel,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                notificationService.getAllNotifications(
                        status, channel, page, size
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotificationById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getNotificationById(id));
    }



    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(notificationService.getNotificationsByCustomer(customerId));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(notificationService.getNotificationsByVehicle(vehicleId));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<NotificationResponse> retryNotification(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.retryNotification(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}
