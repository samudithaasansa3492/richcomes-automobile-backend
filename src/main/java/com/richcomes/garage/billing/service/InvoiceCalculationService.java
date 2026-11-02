package com.richcomes.garage.billing.service;

import com.richcomes.garage.billing.dto.InvoiceItemRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvoiceCalculationService {

    public BigDecimal calculateLineAmount(
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit price cannot be negative"
            );
        }

        return quantity.multiply(unitPrice);
    }

    public BigDecimal calculateSubtotal(List<InvoiceItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invoice must contain at least one item"
            );
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        for (InvoiceItemRequest item : items) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "Invoice item cannot be null"
                );
            }

            BigDecimal lineAmount = calculateLineAmount(
                    item.getQuantity(),
                    item.getUnitPrice()
            );

            subtotal = subtotal.add(lineAmount);
        }

        return subtotal;
    }
}