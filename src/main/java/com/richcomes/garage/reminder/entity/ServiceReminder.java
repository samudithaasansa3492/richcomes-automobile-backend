package com.richcomes.garage.reminder.entity;

import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Main functions:
 * - Store next service/reminder date
 * - Track reminder status
 * - Trigger SMS/system notification
 * - Store optional AI suggested date
 */
@Entity
@Table(name = "service_reminders", schema = "garage")
public class ServiceReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "service_history_id")
    private Long serviceHistoryId;

    @Column(name = "next_service_date", nullable = false)
    private LocalDate nextServiceDate;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceReminderStatus status = ServiceReminderStatus.PENDING;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "ai_suggested_date")
    private LocalDate aiSuggestedDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ServiceReminder() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ServiceReminderStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getNotificationId() { return notificationId; }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Long getId() {
        return id;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getServiceHistoryId() {
        return serviceHistoryId;
    }

    public void setServiceHistoryId(Long serviceHistoryId) {
        this.serviceHistoryId = serviceHistoryId;
    }

    public LocalDate getNextServiceDate() {
        return nextServiceDate;
    }

    public void setNextServiceDate(LocalDate nextServiceDate) {
        this.nextServiceDate = nextServiceDate;
    }

    public LocalDate getReminderDate() {
        return reminderDate;
    }

    public void setReminderDate(LocalDate reminderDate) {
        this.reminderDate = reminderDate;
    }

    public ServiceReminderStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceReminderStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDate getAiSuggestedDate() {
        return aiSuggestedDate;
    }

    public void setAiSuggestedDate(LocalDate aiSuggestedDate) {
        this.aiSuggestedDate = aiSuggestedDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
