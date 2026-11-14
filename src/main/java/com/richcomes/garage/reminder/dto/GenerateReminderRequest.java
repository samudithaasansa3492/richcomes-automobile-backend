package com.richcomes.garage.reminder.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class GenerateReminderRequest {

    @NotNull(message = "Service history ID is required")
    @Positive(message = "Service history ID must be positive")
    private Long serviceHistoryId;

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    private Long customerId;


    public GenerateReminderRequest() {
    }

    public Long getServiceHistoryId() {
        return serviceHistoryId;
    }

    public void setServiceHistoryId(Long serviceHistoryId) {
        this.serviceHistoryId = serviceHistoryId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

}
