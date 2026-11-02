package com.richcomes.garage.billing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.richcomes.garage.billing.dto.InvoiceItemRequest;
import java.util.List;

class InvoiceCalculationServiceTest {

    private final InvoiceCalculationService service =
            new InvoiceCalculationService();

    @Test
    void calculatesFractionalQuantityCorrectly() {
        BigDecimal result = service.calculateLineAmount(
                new BigDecimal("2.5"),
                new BigDecimal("1000.00")
        );

        assertEquals(0, new BigDecimal("2500.00").compareTo(result));
    }

    @Test
    void allowsZeroPrice() {
        BigDecimal result = service.calculateLineAmount(
                BigDecimal.ONE,
                BigDecimal.ZERO
        );

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void rejectsInvalidQuantities() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateLineAmount(null, BigDecimal.ONE));

        assertThrows(IllegalArgumentException.class,
                () -> service.calculateLineAmount(
                        BigDecimal.ZERO, BigDecimal.ONE));

        assertThrows(IllegalArgumentException.class,
                () -> service.calculateLineAmount(
                        new BigDecimal("-1"), BigDecimal.ONE));
    }

    @Test
    void rejectsInvalidPrices() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateLineAmount(BigDecimal.ONE, null));

        assertThrows(IllegalArgumentException.class,
                () -> service.calculateLineAmount(
                        BigDecimal.ONE, new BigDecimal("-1")));
    }

    @Test
    void calculatesSubtotalForMultipleItems() {
        InvoiceItemRequest labour = new InvoiceItemRequest();
        labour.setQuantity(new BigDecimal("1"));
        labour.setUnitPrice(new BigDecimal("1500.00"));

        InvoiceItemRequest oil = new InvoiceItemRequest();
        oil.setQuantity(new BigDecimal("2.5"));
        oil.setUnitPrice(new BigDecimal("2000.00"));

        BigDecimal result = service.calculateSubtotal(
                List.of(labour, oil)
        );

        assertEquals(0, new BigDecimal("6500.00").compareTo(result));
    }
}
