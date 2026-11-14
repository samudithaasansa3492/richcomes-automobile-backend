package com.richcomes.garage.reminder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import jakarta.validation.constraints.Positive;

public class ServiceReminderCreateRequest {

    @NotNull(message = "Vehicle ID is required")
    @Positive(message = "Vehicle ID must be positive")
    private Long vehicleId;

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    private Long customerId;

    @Positive(message = "Service history ID must be positive")
    private Long serviceHistoryId;

    @NotNull(message = "Next service date is required")
    private LocalDate nextServiceDate;

    @NotNull(message = "Reminder date is required")
    private LocalDate reminderDate;

    @NotBlank(message = "Reminder message cannot be blank")
    private String message;

    private LocalDate aiSuggestedDate;

    public ServiceReminderCreateRequest() {
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
}
