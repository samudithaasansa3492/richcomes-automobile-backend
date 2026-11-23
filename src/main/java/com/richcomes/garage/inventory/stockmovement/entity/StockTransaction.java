
package com.richcomes.garage.inventory.stockmovement.entity;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transactions", schema = "garage")
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    // Nullable for historical records whose source store is unknown.
    // New store-specific movements always set this relation.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "store_location_id",
            foreignKey = @ForeignKey(name = "fk_stock_transactions_store_location")
    )
    private StoreLocation storeLocation;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "transaction_type",
            nullable = false,
            length = 30
    )
    private StockTransactionType transactionType;

    // Decimal quantity support
    @Column(
            name = "quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantity;

    // Quantity before transaction
    @Column(
            name = "quantity_before",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantityBefore;

    // Quantity after transaction
    @Column(
            name = "quantity_after",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantityAfter;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 500)
    private String reason;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public StockTransaction() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public void setStockItem(StockItem stockItem) {
        this.stockItem = stockItem;
    }

    public StoreLocation getStoreLocation() {
        return storeLocation;
    }

    public void setStoreLocation(StoreLocation storeLocation) {
        this.storeLocation = storeLocation;
    }

    public StockTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(
            StockTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public void setQuantityBefore(BigDecimal quantityBefore) {
        this.quantityBefore = quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public void setQuantityAfter(BigDecimal quantityAfter) {
        this.quantityAfter = quantityAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(Long performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
