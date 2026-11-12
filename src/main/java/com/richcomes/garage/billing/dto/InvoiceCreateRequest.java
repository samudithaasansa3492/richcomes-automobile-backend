package com.richcomes.garage.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class InvoiceCreateRequest {

    @NotNull(message = "Service job ID is required")
    @Positive(message = "Service job ID must be positive")
    private Long serviceJobId;

    public InvoiceCreateRequest() {
    }

    public Long getServiceJobId() {
        return serviceJobId;
    }

    public void setServiceJobId(Long serviceJobId) {
        this.serviceJobId = serviceJobId;
    }
}