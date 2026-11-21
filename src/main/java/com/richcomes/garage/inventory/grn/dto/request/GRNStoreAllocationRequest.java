
package com.richcomes.garage.inventory.grn.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class GRNStoreAllocationRequest {

    @NotNull(message = "Store location ID is required")
    @Positive(message = "Store location ID must be positive")
    private Long storeLocationId;

    @NotNull(message = "Allocated quantity is required")
    @DecimalMin(
            value = "0.000",
            inclusive = false,
            message = "Allocated quantity must be greater than zero"
    )
    @Digits(
            integer = 11,
            fraction = 3,
            message = "Allocated quantity supports up to 3 decimal places"
    )
    private BigDecimal allocatedQuantity;

    public GRNStoreAllocationRequest() {
    }

    public Long getStoreLocationId() {
        return storeLocationId;
    }

    public void setStoreLocationId(Long storeLocationId) {
        this.storeLocationId = storeLocationId;
    }

    public BigDecimal getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public void setAllocatedQuantity(BigDecimal allocatedQuantity) {
        this.allocatedQuantity = allocatedQuantity;
    }
}
