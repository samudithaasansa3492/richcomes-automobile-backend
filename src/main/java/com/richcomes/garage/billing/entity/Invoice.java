package com.richcomes.garage.billing.entity;

import com.richcomes.garage.billing.enums.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_invoices", schema = "garage")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_job_id", nullable = false)
    private Integer serviceJobId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "invoice_no", length = 50, unique = true)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDateTime invoiceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    protected Invoice() {
        // Required by JPA.
    }

    public Invoice(
            Integer serviceJobId,
            Long customerId,
            Long vehicleId,
            LocalDateTime invoiceDate,
            BigDecimal subtotal
    ) {
        this.serviceJobId = serviceJobId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.invoiceDate = invoiceDate;
        this.subtotal = subtotal;
        this.totalAmount = subtotal;
    }

    public Long getId() {
        return id;
    }

    public Integer getServiceJobId() {
        return serviceJobId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public LocalDateTime getInvoiceDate() {
        return invoiceDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
