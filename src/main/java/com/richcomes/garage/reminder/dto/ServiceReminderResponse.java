package com.richcomes.garage.reminder.dto;

import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ServiceReminderResponse {

    private Long id;
    private Long vehicleId;
    private Long customerId;
    private Long serviceHistoryId;
    private Long notificationId;
    private LocalDate nextServiceDate;
    private LocalDate reminderDate;
    private ServiceReminderStatus status;
    private String message;
    private LocalDate aiSuggestedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }

    public ServiceReminderResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
