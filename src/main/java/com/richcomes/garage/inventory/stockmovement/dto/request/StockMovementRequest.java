
package com.richcomes.garage.inventory.stockmovement.dto.request;

import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StockMovementRequest {

    @NotNull(message = "Stock item ID is required")
    private Long stockItemId;

    @NotNull(message = "Transaction type is required")
    private StockTransactionType transactionType;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than zero")
    private Integer quantity;

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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}
