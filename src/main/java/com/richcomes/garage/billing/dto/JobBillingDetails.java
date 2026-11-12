package com.richcomes.garage.billing.dto;

import java.util.List;

public class JobBillingDetails {

    private Long serviceJobId;
    private Long customerId;
    private Long vehicleId;
    private String jobNo;
    private String jobStatus;
    private List<InvoiceItemRequest> items;

    public JobBillingDetails() {
    }

    public Long getServiceJobId() {
        return serviceJobId;
    }

    public void setServiceJobId(Long serviceJobId) {
        this.serviceJobId = serviceJobId;
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

    public String getJobNo() {
        return jobNo;
    }

    public void setJobNo(String jobNo) {
        this.jobNo = jobNo;
    }

    public String getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(String jobStatus) {
        this.jobStatus = jobStatus;
    }

    public List<InvoiceItemRequest> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItemRequest> items) {
        this.items = items;
    }
}
