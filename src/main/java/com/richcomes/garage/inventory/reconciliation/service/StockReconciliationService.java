package com.richcomes.garage.inventory.reconciliation.service;

import com.richcomes.garage.inventory.reconciliation.dto.StockReconciliationResponse;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockReconciliationService {

    private final StockItemRepository stockItemRepository;
    private final StoreStockBalanceRepository storeBalanceRepository;

    public StockReconciliationService(StockItemRepository stockItemRepository,
                                      StoreStockBalanceRepository storeBalanceRepository) {
        this.stockItemRepository = stockItemRepository;
        this.storeBalanceRepository = storeBalanceRepository;
    }

    // Consistent PostgreSQL snapshot; this method NEVER changes stock or history.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<StockReconciliationResponse> report(boolean mismatchesOnly) {
        List<StockItem> items = stockItemRepository.findAll();
        List<StoreStockBalance> balances = storeBalanceRepository.findAll();
        Map<Long, BigDecimal> sums = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Boolean> invalid = new HashMap<>();

        for (StoreStockBalance balance : balances) {
            Long itemId = balance.getStockItem().getId();
            BigDecimal quantity = balance.getQuantity();
            counts.merge(itemId, 1, Integer::sum);
            if (!valid(quantity, balance.getStockItem().getUnit())) {
                invalid.put(itemId, true);
                continue;
            }
            sums.merge(itemId, quantity, BigDecimal::add);
        }

        return items.stream().map(item -> {
            BigDecimal global = item.getCurrentQuantity();
            BigDecimal total = sums.getOrDefault(item.getId(), BigDecimal.ZERO);
            boolean bad = Boolean.TRUE.equals(invalid.get(item.getId()))
                    || !valid(global, item.getUnit());
            BigDecimal difference = global == null ? null : global.subtract(total);
            String status = bad ? "INVALID_BALANCE"
                    : difference.compareTo(BigDecimal.ZERO) == 0 ? "MATCH" : "MISMATCH";
            return new StockReconciliationResponse(
                    item.getId(), item.getItemCode(), item.getItemName(), item.getUnit(),
                    global, total, difference, counts.getOrDefault(item.getId(), 0), status);
        }).filter(row -> !mismatchesOnly || !"MATCH".equals(row.status()))
          .sorted((a,b) -> a.stockItemId().compareTo(b.stockItemId()))
          .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StockReconciliationResponse reportItem(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid stock item ID is required");
        }
        // All-items snapshot keeps item and store quantities consistent.
        return report(false).stream()
                .filter(r -> itemId.equals(r.stockItemId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock item not found"));
    }

    private boolean valid(BigDecimal quantity, String unit) {
        if (quantity == null || quantity.signum() < 0) return false;
        BigDecimal n = quantity.stripTrailingZeros();
        int fractionDigits = Math.max(0, n.scale());
        int integerDigits = Math.max(0, n.precision() - n.scale());
        if (fractionDigits > 3 || integerDigits > 11) return false;
        if ("PCS".equals(unit)) return fractionDigits == 0;
        return "L".equals(unit) || "ML".equals(unit);
    }
}
