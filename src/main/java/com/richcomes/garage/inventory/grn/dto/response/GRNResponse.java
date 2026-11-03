
package com.richcomes.garage.inventory.grn.dto.response;

import com.richcomes.garage.inventory.grn.enums.GRNStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class GRNResponse {

    private Long id;
    private String grnNumber;

    private Long supplierId;
    private String supplierName;

    private GRNStatus status;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
    private String notes;

    private List<GRNItemResponse> items;
    private BigDecimal totalAmount;

    public GRNResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGrnNumber() {
        return grnNumber;
    }

    public void setGrnNumber(String grnNumber) {
        this.grnNumber = grnNumber;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public GRNStatus getStatus() {
        return status;
    }

    public void setStatus(GRNStatus status) {
        this.status = status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<GRNItemResponse> getItems() {
        return items;
    }

    public void setItems(List<GRNItemResponse> items) {
        this.items = items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
