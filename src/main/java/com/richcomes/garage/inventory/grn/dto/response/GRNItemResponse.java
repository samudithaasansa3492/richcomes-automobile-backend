
package com.richcomes.garage.inventory.grn.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GRNItemResponse {

    private Long id;
    private Long stockItemId;
    private String itemCode;
    private String itemName;

    private BigDecimal receivedQuantity;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;

    // Store-wise allocation details for this GRN item.
    private List<StoreAllocationResponse> allocations =
            new ArrayList<>();

    public GRNItemResponse() {
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

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(BigDecimal receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public List<StoreAllocationResponse> getAllocations() {
        return allocations;
    }

    public void setAllocations(
            List<StoreAllocationResponse> allocations) {

        this.allocations = allocations == null
                ? new ArrayList<>()
                : new ArrayList<>(allocations);
    }

    // DTO returned to the frontend for each store allocation.
    public record StoreAllocationResponse(
            Long storeLocationId,
            String storeCode,
            String storeName,
            BigDecimal allocatedQuantity
    ) {
    }
}
