
package com.richcomes.garage.inventory.grn.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GRNItemRequest {

    @NotNull(message = "Stock item ID is required")
    private Long stockItemId;

    @NotNull(message = "Received quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer receivedQuantity;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(
            value = "0.00",
            message = "Unit cost cannot be negative"
    )
    private BigDecimal unitCost;

    public GRNItemRequest() {
    }

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(Integer receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }
}
