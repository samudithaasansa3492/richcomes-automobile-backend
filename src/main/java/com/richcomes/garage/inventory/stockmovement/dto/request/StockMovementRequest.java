
package com.richcomes.garage.inventory.stockmovement.dto.request;

import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class StockMovementRequest {

    @NotNull(message = "Stock item ID is required")
    @Positive(message = "Stock item ID must be positive")
    private Long stockItemId;

    @NotNull(message = "Store location ID is required")
    @Positive(message = "Store location ID must be positive")
    private Long storeLocationId;

    @NotNull(message = "Transaction type is required")
    private StockTransactionType transactionType;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.000", inclusive = false)
    @Digits(integer = 11, fraction = 3)
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

    public Long getStoreLocationId() {
        return storeLocationId;
    }

    public void setStoreLocationId(Long storeLocationId) {
        this.storeLocationId = storeLocationId;
    }

    public StockTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(StockTransactionType transactionType) {
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
