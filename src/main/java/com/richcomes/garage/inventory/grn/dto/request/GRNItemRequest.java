
package com.richcomes.garage.inventory.grn.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GRNItemRequest {

    @NotNull(message = "Stock item ID is required")
    private Long stockItemId;

    // Whole numbers and decimal quantities supported.
    @NotNull(message = "Received quantity is required")
    @DecimalMin(
            value = "0.000",
            inclusive = false,
            message = "Received quantity must be greater than zero"
    )
    @Digits(
            integer = 11,
            fraction = 3,
            message = "Quantity supports up to 3 decimal places"
    )
    private BigDecimal receivedQuantity;

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
}
