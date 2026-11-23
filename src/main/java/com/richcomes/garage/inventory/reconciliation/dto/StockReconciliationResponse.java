package com.richcomes.garage.inventory.reconciliation.dto;

import java.math.BigDecimal;

public record StockReconciliationResponse(
        Long stockItemId,
        String itemCode,
        String itemName,
        String unit,
        BigDecimal globalQuantity,
        BigDecimal totalStoreQuantity,
        BigDecimal difference,
        int storeBalanceCount,
        String status
) {}
