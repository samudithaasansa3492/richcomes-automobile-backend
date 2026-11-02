
package com.richcomes.garage.inventory.grn.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class GRNRequest {

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Valid
    @NotEmpty(message = "At least one GRN item is required")
    private List<GRNItemRequest> items;

    public GRNRequest() {
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<GRNItemRequest> getItems() {
        return items;
    }

    public void setItems(List<GRNItemRequest> items) {
        this.items = items;
    }
}
