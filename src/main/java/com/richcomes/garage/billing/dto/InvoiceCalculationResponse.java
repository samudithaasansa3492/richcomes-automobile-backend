package com.richcomes.garage.billing.dto;

import java.math.BigDecimal;

public class InvoiceCalculationResponse {

    private BigDecimal subtotal;

    public InvoiceCalculationResponse() {
    }

    public InvoiceCalculationResponse(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
