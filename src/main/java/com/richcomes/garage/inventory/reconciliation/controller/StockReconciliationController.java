package com.richcomes.garage.inventory.reconciliation.controller;

import com.richcomes.garage.inventory.reconciliation.dto.StockReconciliationResponse;
import com.richcomes.garage.inventory.reconciliation.service.StockReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/stock-transactions/reconciliation")
public class StockReconciliationController {

    private final StockReconciliationService service;

    public StockReconciliationController(StockReconciliationService service) {
        this.service = service;
    }

    // Covered by existing GET /api/inventory/stock-transactions/** inventory:read rule.
    @GetMapping
    public List<StockReconciliationResponse> getReport(
            @RequestParam(defaultValue = "false") boolean mismatchesOnly) {
        return service.report(mismatchesOnly);
    }

    @GetMapping("/{itemId}")
    public StockReconciliationResponse getItem(@PathVariable Long itemId) {
        return service.reportItem(itemId);
    }
}
