package com.richcomes.garage.billing.entity;

import com.richcomes.garage.billing.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", schema = "garage")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(length = 500)
    private String notes;

    protected Payment() {
        // Required by JPA.
    }

    public Payment(
            Invoice invoice,
            LocalDateTime paymentDate,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            String referenceNo,
            String notes
    ) {
        this.invoice = invoice;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.referenceNo = referenceNo;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public String getNotes() {
        return notes;
    }
}