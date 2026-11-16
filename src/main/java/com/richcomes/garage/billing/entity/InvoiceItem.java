package com.richcomes.garage.billing.entity;

import com.richcomes.garage.billing.enums.InvoiceItemType;
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

@Entity
@Table(name = "sales_invoice_items", schema = "garage")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private InvoiceItemType itemType;

    @Column(name = "item_reference_id")
    private Long itemReferenceId;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    protected InvoiceItem() {
        // Required by JPA.
    }

    public InvoiceItem(
            Invoice invoice,
            InvoiceItemType itemType,
            Long itemReferenceId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount
    ) {
        this.invoice = invoice;
        this.itemType = itemType;
        this.itemReferenceId = itemReferenceId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
    }

    public Long getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public InvoiceItemType getItemType() {
        return itemType;
    }

    public Long getItemReferenceId() {
        return itemReferenceId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
