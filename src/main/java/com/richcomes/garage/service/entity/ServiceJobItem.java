package com.richcomes.garage.service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "service_job_items")
public class ServiceJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ප්‍රධාන Job Card එකට සම්බන්ධ කිරීම
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_job_id", nullable = false)
    private ServiceJob serviceJob;

    // සිදුකරන සේවාවට (Service Item) සම්බන්ධ කිරීම
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_item_id", nullable = false)
    private ServiceItem serviceItem;

    @Column(name = "labour_hours")
    private Double labourHours;

    @Column(name = "labour_rate")
    private BigDecimal labourRate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    public ServiceJobItem() {
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ServiceJob getServiceJob() { return serviceJob; }
    public void setServiceJob(ServiceJob serviceJob) { this.serviceJob = serviceJob; }

    public ServiceItem getServiceItem() { return serviceItem; }
    public void setServiceItem(ServiceItem serviceItem) { this.serviceItem = serviceItem; }

    public Double getLabourHours() { return labourHours; }
    public void setLabourHours(Double labourHours) { this.labourHours = labourHours; }

    public BigDecimal getLabourRate() { return labourRate; }
    public void setLabourRate(BigDecimal labourRate) { this.labourRate = labourRate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}