package com.richcomes.garage.service.dto;

import java.math.BigDecimal;

public class ServiceJobItemDTO {
    private Long id;
    private Long serviceJobId;
    private Long serviceItemId;
    private Double labourHours;
    private BigDecimal labourRate;
    private BigDecimal totalAmount;

    public ServiceJobItemDTO() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(Long serviceJobId) { this.serviceJobId = serviceJobId; }

    public Long getServiceItemId() { return serviceItemId; }
    public void setServiceItemId(Long serviceItemId) { this.serviceItemId = serviceItemId; }

    public Double getLabourHours() { return labourHours; }
    public void setLabourHours(Double labourHours) { this.labourHours = labourHours; }

    public BigDecimal getLabourRate() { return labourRate; }
    public void setLabourRate(BigDecimal labourRate) { this.labourRate = labourRate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}