package com.richcomes.garage.inventory.stockmovement.service;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.stockmovement.dto.request.StockMovementRequest;
import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StockMovementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockItemRepository itemRepository;
    private final StockTransactionRepository transactionRepository;
    private final StoreLocationRepository storeLocationRepository;
    private final StoreStockBalanceRepository storeStockBalanceRepository;

    public StockMovementService(
            StockItemRepository itemRepository,
            StockTransactionRepository transactionRepository,
            StoreLocationRepository storeLocationRepository,
            StoreStockBalanceRepository storeStockBalanceRepository) {

        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.storeLocationRepository = storeLocationRepository;
        this.storeStockBalanceRepository = storeStockBalanceRepository;
    }

    // RECORD A MOVEMENT AGAINST ONE SELECTED STORE.
    // The controller must continue to reject direct STOCK_IN and STOCK_OUT:
    // GRNs and Job Cards have their own trusted workflows.
    @Transactional
    public StockMovementResponse recordMovement(StockMovementRequest request) {

        if (request == null
                || request.getStockItemId() == null
                || request.getStockItemId() <= 0
                || request.getStoreLocationId() == null
                || request.getStoreLocationId() <= 0
                || request.getTransactionType() == null
                || !isValidQuantity(request.getQuantity())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock item, store, transaction type and positive quantity are required"
            );
        }

        if (request.getReason() != null && request.getReason().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reason cannot exceed 500 characters"
            );
        }

        StockTransactionType type = request.getTransactionType();
        BigDecimal quantity = request.getQuantity();

        // Enforce this rule inside the service as well as at the controller boundary.
        if (type == StockTransactionType.STOCK_IN
                || type == StockTransactionType.STOCK_OUT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Direct STOCK_IN/STOCK_OUT is not permitted. Use GRN or Job Card workflows."
            );
        }

        if ((type == StockTransactionType.DAMAGE
                || type == StockTransactionType.WASTAGE
                || type == StockTransactionType.ADJUSTMENT_IN
                || type == StockTransactionType.ADJUSTMENT_OUT)
                && (request.getReason() == null || request.getReason().isBlank())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reason is required for this transaction"
            );
        }

        // Lock order: global item first, then store balance; same order as Job Card.
        StockItem item = itemRepository.findByIdForUpdate(request.getStockItemId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Stock item not found"));

        if (!item.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot move stock for inactive item");
        }

        validateQuantityForUnit(item, quantity);

        BigDecimal globalBefore = item.getCurrentQuantity();
        validateExistingBalance(item, globalBefore, "global stock");

        Long storeId = request.getStoreLocationId();
        StoreLocation store = storeLocationRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Store location not found"));

        if (!store.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot adjust stock for an inactive store");
        }

        // Do not silently create a store balance: global stock may not yet be reconciled
        // across locations. A missing row requires explicit inventory setup/reconciliation.
        StoreStockBalance balance = storeStockBalanceRepository
                .findForUpdate(storeId, item.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Selected store balance is missing; inventory reconciliation required"));

        BigDecimal storeBefore = balance.getQuantity();
        validateExistingBalance(item, storeBefore, "selected store stock");

        boolean increase = type == StockTransactionType.ADJUSTMENT_IN;
        BigDecimal globalAfter = increase
                ? globalBefore.add(quantity)
                : globalBefore.subtract(quantity);
        BigDecimal storeAfter = increase
                ? storeBefore.add(quantity)
                : storeBefore.subtract(quantity);

        if (globalAfter.compareTo(ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient global stock. Available: " + globalBefore.toPlainString());
        }

        if (storeAfter.compareTo(ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient stock in selected store. Available: "
                            + storeBefore.toPlainString());
        }

        if (!fitsQuantityColumn(globalAfter) || !fitsQuantityColumn(storeAfter)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stock quantity exceeds supported range");
        }

        // Both updates and the transaction log commit/roll back together.
        balance.setQuantity(storeAfter);
        storeStockBalanceRepository.saveAndFlush(balance);

        item.setCurrentQuantity(globalAfter);
        itemRepository.saveAndFlush(item);

        StockTransaction transaction = new StockTransaction();
        transaction.setStockItem(item);
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setQuantityBefore(globalBefore);
        transaction.setQuantityAfter(globalAfter);
        transaction.setReason(request.getReason());

        // StockTransaction currently has no storeLocation mapping. Record an explicit
        // store reference in the audit fields until a dedicated store FK is migrated.
        transaction.setReferenceType("STORE_LOCATION");
        transaction.setReferenceId(storeId);

        // Authenticated actor / performedBy requires separate auth integration.
        StockTransaction saved = transactionRepository.saveAndFlush(transaction);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getItemTransactions(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Valid stock item ID is required");
        }
        if (!itemRepository.existsById(itemId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Stock item not found");
        }
        return transactionRepository.findByStockItemIdOrderByCreatedAtDesc(itemId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void validateQuantityForUnit(StockItem item, BigDecimal quantity) {
        if (!isValidQuantity(quantity)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid stock movement quantity");
        }
        String unit = item.getUnit();
        if ("PCS".equals(unit)) {
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PCS items require whole-number quantities");
            }
        } else if (!"L".equals(unit) && !"ML".equals(unit)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock item has an invalid measurement unit");
        }
    }

    private void validateExistingBalance(
            StockItem item, BigDecimal quantity, String label) {
        if (quantity == null
                || quantity.compareTo(ZERO) < 0
                || !fitsQuantityColumn(quantity)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Invalid " + label + " quantity");
        }
        if ("PCS".equals(item.getUnit())
                && quantity.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "PCS item has fractional " + label + " balance");
        }
    }

    private boolean isValidQuantity(BigDecimal quantity) {
        return quantity != null
                && quantity.compareTo(ZERO) > 0
                && fitsQuantityColumn(quantity);
    }

    private boolean fitsQuantityColumn(BigDecimal quantity) {
        if (quantity == null) {
            return false;
        }
        BigDecimal normalized = quantity.stripTrailingZeros();
        int fractionDigits = Math.max(0, normalized.scale());
        int integerDigits = Math.max(0,
                normalized.precision() - normalized.scale());
        return fractionDigits <= 3 && integerDigits <= 11;
    }

    private StockMovementResponse mapToResponse(StockTransaction transaction) {
        StockMovementResponse response = new StockMovementResponse();
        StockItem item = transaction.getStockItem();

        response.setId(transaction.getId());
        response.setStockItemId(item.getId());
        response.setItemCode(item.getItemCode());
        response.setItemName(item.getItemName());
        response.setTransactionType(transaction.getTransactionType());
        response.setQuantity(transaction.getQuantity());
        response.setQuantityBefore(transaction.getQuantityBefore());
        response.setQuantityAfter(transaction.getQuantityAfter());
        response.setReferenceType(transaction.getReferenceType());
        response.setReferenceId(transaction.getReferenceId());
        response.setReason(transaction.getReason());
        response.setPerformedBy(transaction.getPerformedBy());
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }
}
