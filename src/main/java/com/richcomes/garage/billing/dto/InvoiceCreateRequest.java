package com.richcomes.garage.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class InvoiceCreateRequest {

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    private Long customerId;

    @Positive(message = "Vehicle ID must be positive")
    private Long vehicleId;

    @Positive(message = "Service job ID must be positive")
    private Long serviceJobId;

    @NotNull(message = "Branch ID is required")
    @Positive(message = "Branch ID must be positive")
    private Long branchId;

    @NotEmpty(message = "Invoice must contain at least one item")
    @Valid
    private List<
            @NotNull(message = "Invoice item cannot be null")
                    InvoiceItemRequest> items;

    public InvoiceCreateRequest() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getServiceJobId() {
        return serviceJobId;
    }

    public void setServiceJobId(Long serviceJobId) {
        this.serviceJobId = serviceJobId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public List<InvoiceItemRequest> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItemRequest> items) {
        this.items = items;
    }
}
