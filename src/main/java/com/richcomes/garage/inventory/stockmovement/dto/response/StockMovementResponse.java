
package com.richcomes.garage.inventory.stockmovement.dto.response;

import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockMovementResponse {

    private Long id;
    private Long stockItemId;
    private Long storeLocationId;
    private String storeCode;
    private String storeName;
    private String itemCode;
    private String itemName;

    private StockTransactionType transactionType;

    // Decimal quantity support
    private BigDecimal quantity;
    private BigDecimal quantityBefore;
    private BigDecimal quantityAfter;

    private String referenceType;
    private Long referenceId;
    private String reason;
    private Long performedBy;
    private LocalDateTime createdAt;

    public StockMovementResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
    }

    public Long getStoreLocationId() {
        return storeLocationId;
    }

    public void setStoreLocationId(Long storeLocationId) {
        this.storeLocationId = storeLocationId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
