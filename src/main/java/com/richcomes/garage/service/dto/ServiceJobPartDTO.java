package com.richcomes.garage.service.dto;

import java.math.BigDecimal;

public class ServiceJobPartDTO {
    private Long id;
    private Long serviceJobId;
    private Long partId;
    private Double quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;

    public ServiceJobPartDTO() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(Long serviceJobId) { this.serviceJobId = serviceJobId; }

    public Long getPartId() { return partId; }
    public void setPartId(Long partId) { this.partId = partId; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}