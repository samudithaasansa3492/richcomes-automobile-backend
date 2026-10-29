package com.richcomes.garage.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class InvoiceCalculationRequest {

    @NotEmpty(message = "Invoice must contain at least one item")
    @Valid
    private List<
            @NotNull(message = "Invoice item cannot be null")
                    InvoiceItemRequest> items;

    public InvoiceCalculationRequest() {
    }

    public List<InvoiceItemRequest> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItemRequest> items) {
        this.items = items;
    }
}
