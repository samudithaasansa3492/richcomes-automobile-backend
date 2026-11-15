
package com.richcomes.garage.inventory.stockmovement.dto.request;

import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class StockMovementRequest {

    @NotNull(message = "Stock item ID is required")
    private Long stockItemId;

    @NotNull(message = "Transaction type is required")
    private StockTransactionType transactionType;

    // Supports both whole-number and decimal quantities
    @NotNull(message = "Quantity is required")
    @DecimalMin(
            value = "0.000",
            inclusive = false,
            message = "Quantity must be greater than zero"
    )
    @Digits(
            integer = 11,
            fraction = 3,
            message = "Quantity supports up to 3 decimal places"
    )
    private BigDecimal quantity;

    @Size(max = 500)
    private String reason;

    public StockMovementRequest() {
    }

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
