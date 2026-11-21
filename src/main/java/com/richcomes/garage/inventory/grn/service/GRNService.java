package com.richcomes.garage.inventory.grn.service;

import com.richcomes.garage.inventory.grn.dto.request.GRNItemRequest;
import com.richcomes.garage.inventory.grn.dto.request.GRNRequest;
import com.richcomes.garage.inventory.grn.dto.request.GRNStoreAllocationRequest;
import com.richcomes.garage.inventory.grn.dto.response.GRNItemResponse;
import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.entity.GRN;
import com.richcomes.garage.inventory.grn.entity.GRNItem;
import com.richcomes.garage.inventory.grn.entity.GRNItemAllocation;
import com.richcomes.garage.inventory.grn.enums.GRNStatus;
import com.richcomes.garage.inventory.grn.repository.GRNRepository;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
import com.richcomes.garage.inventory.supplier.entity.Supplier;
import com.richcomes.garage.inventory.supplier.repository.SupplierRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GRNService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final GRNRepository grnRepository;
    private final SupplierRepository supplierRepository;
    private final StockItemRepository stockItemRepository;
    private final StockTransactionRepository transactionRepository;
    private final StoreLocationRepository storeLocationRepository;
    private final StoreStockBalanceRepository storeStockBalanceRepository;

    public GRNService(
            GRNRepository grnRepository,
            SupplierRepository supplierRepository,
            StockItemRepository stockItemRepository,
            StockTransactionRepository transactionRepository,
            StoreLocationRepository storeLocationRepository,
            StoreStockBalanceRepository storeStockBalanceRepository) {
        this.grnRepository = grnRepository;
        this.supplierRepository = supplierRepository;
        this.stockItemRepository = stockItemRepository;
        this.transactionRepository = transactionRepository;
        this.storeLocationRepository = storeLocationRepository;
        this.storeStockBalanceRepository = storeStockBalanceRepository;
    }

    // DRAFT: persist allocations, but do not alter any stock balance.
    @Transactional
    public GRNResponse createDraft(GRNRequest request) {
        if (request == null || request.getSupplierId() == null
                || request.getItems() == null || request.getItems().isEmpty()) {
            throw badRequest("Supplier and at least one item are required");
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> notFound("Supplier not found"));
        if (!supplier.isActive()) {
            throw badRequest("Inactive supplier cannot be used for a GRN");
        }
        if (request.getNotes() != null && request.getNotes().length() > 500) {
            throw badRequest("GRN notes cannot exceed 500 characters");
        }

        GRN grn = new GRN();
        grn.setSupplier(supplier);
        grn.setNotes(request.getNotes());
        grn.setGrnNumber("GRN-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase());

        Set<Long> itemIds = new HashSet<>();
        for (GRNItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getStockItemId() == null
                    || !isValidQuantity(itemRequest.getReceivedQuantity())
                    || !isValidUnitCost(itemRequest.getUnitCost())) {
                throw badRequest("Invalid GRN item quantity or unit cost");
            }
            if (!itemIds.add(itemRequest.getStockItemId())) {
                throw badRequest("Duplicate stock item in GRN");
            }

            StockItem stockItem = stockItemRepository.findById(itemRequest.getStockItemId())
                    .orElseThrow(() -> notFound("Stock item not found"));
            if (!stockItem.isActive()) {
                throw badRequest("Inactive stock item cannot be received");
            }
            validateQuantityForUnit(stockItem, itemRequest.getReceivedQuantity());

            GRNItem grnItem = new GRNItem();
            grnItem.setStockItem(stockItem);
            grnItem.setReceivedQuantity(itemRequest.getReceivedQuantity());
            grnItem.setUnitCost(itemRequest.getUnitCost());

            // Require a complete, explicit store split for every received item.
            List<GRNStoreAllocationRequest> requests = itemRequest.getAllocations();
            if (requests == null || requests.isEmpty()) {
                throw badRequest("Every GRN item needs at least one store allocation");
            }
            Set<Long> storeIds = new HashSet<>();
            BigDecimal allocatedTotal = ZERO;
            for (GRNStoreAllocationRequest allocationRequest : requests) {
                if (allocationRequest == null || allocationRequest.getStoreLocationId() == null
                        || allocationRequest.getStoreLocationId() <= 0
                        || !isValidQuantity(allocationRequest.getAllocatedQuantity())) {
                    throw badRequest("Invalid store allocation");
                }
                Long storeId = allocationRequest.getStoreLocationId();
                if (!storeIds.add(storeId)) {
                    throw badRequest("A GRN item cannot allocate the same store twice");
                }
                StoreLocation store = storeLocationRepository.findById(storeId)
                        .orElseThrow(() -> notFound("Store location not found: " + storeId));
                if (!store.isActive()) {
                    throw badRequest("Inactive stores cannot receive stock");
                }
                BigDecimal quantity = allocationRequest.getAllocatedQuantity();
                validateQuantityForUnit(stockItem, quantity);
                allocatedTotal = allocatedTotal.add(quantity);

                GRNItemAllocation allocation = new GRNItemAllocation();
                allocation.setStoreLocation(store);
                allocation.setAllocatedQuantity(quantity);
                grnItem.addAllocation(allocation);
            }
            if (allocatedTotal.compareTo(itemRequest.getReceivedQuantity()) != 0) {
                throw badRequest("Store allocations must equal the GRN received quantity");
            }
            grn.addItem(grnItem);
        }

        // Cascade from GRN -> GRNItem -> GRNItemAllocation.
        GRN saved = grnRepository.saveAndFlush(grn);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GRNResponse> getAllGRNs() {
        return grnRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public GRNResponse getGRNById(Long id) {
        GRN grn = grnRepository.findById(id)
                .orElseThrow(() -> notFound("GRN not found"));
        return mapToResponse(grn);
    }

    // Supplier GRN history: include DRAFT/CANCELLED for traceability.
    // Consumers should count only CONFIRMED GRNs in actual purchase totals.
    // The SupplierController endpoint will call this method in a separate step.
    @Transactional(readOnly = true)
    public List<GRNResponse> getSupplierPurchaseHistory(Long supplierId) {
        if (supplierId == null || supplierId <= 0) {
            throw badRequest("Valid supplier ID is required");
        }
        // Inactive suppliers still have historical records.
        if (!supplierRepository.existsById(supplierId)) {
            throw notFound("Supplier not found");
        }
        return grnRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // CONFIRM: one transaction updates each allocated store and global item total.
    @Transactional
    public GRNResponse confirmGRN(Long id) {
        GRN grn = grnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("GRN not found"));
        if (grn.getStatus() != GRNStatus.DRAFT) {
            throw conflict("Only DRAFT GRNs can be confirmed");
        }
        if (grn.getItems().isEmpty()) {
            throw badRequest("Cannot confirm an empty GRN");
        }

        // Lock stock items in ascending order; use the same ordering for all GRNs.
        List<GRNItem> sortedItems = grn.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getStockItem().getId()))
                .toList();

        for (GRNItem grnItem : sortedItems) {
            Long itemId = grnItem.getStockItem().getId();
            StockItem stockItem = stockItemRepository.findByIdForUpdate(itemId)
                    .orElseThrow(() -> notFound("Stock item not found: " + itemId));
            if (!stockItem.isActive()) {
                throw badRequest("Cannot receive inactive stock item");
            }
            BigDecimal received = grnItem.getReceivedQuantity();
            validateQuantityForUnit(stockItem, received);

            List<GRNItemAllocation> allocations = grnItem.getAllocations();
            if (allocations == null || allocations.isEmpty()) {
                throw badRequest("Cannot confirm GRN item without store allocations");
            }
            Set<Long> seenStores = new HashSet<>();
            BigDecimal allocatedTotal = ZERO;
            for (GRNItemAllocation allocation : allocations) {
                if (allocation == null || allocation.getStoreLocation() == null) {
                    throw badRequest("Invalid saved GRN store allocation");
                }
                Long storeId = allocation.getStoreLocation().getId();
                if (storeId == null || !seenStores.add(storeId)) {
                    throw badRequest("Duplicate or invalid store allocation");
                }
                validateQuantityForUnit(stockItem, allocation.getAllocatedQuantity());
                allocatedTotal = allocatedTotal.add(allocation.getAllocatedQuantity());
            }
            if (allocatedTotal.compareTo(received) != 0) {
                throw badRequest("Store allocations must equal the GRN received quantity");
            }

            BigDecimal before = stockItem.getCurrentQuantity();
            if (before == null || before.compareTo(ZERO) < 0 || !fitsQuantityColumn(before)) {
                throw conflict("Invalid current stock quantity");
            }
            BigDecimal after = before.add(received);
            if (!fitsQuantityColumn(after)) {
                throw badRequest("Stock quantity exceeds supported range");
            }

            // The item row lock serializes GRN receipts for this product, including
            // first-time balance inserts where a store balance row cannot yet be locked.
            for (GRNItemAllocation allocation : allocations.stream()
                    .sorted(Comparator.comparing(a -> a.getStoreLocation().getId()))
                    .toList()) {
                Long storeId = allocation.getStoreLocation().getId();
                StoreLocation store = storeLocationRepository.findById(storeId)
                        .orElseThrow(() -> notFound("Store location not found: " + storeId));
                if (!store.isActive()) {
                    throw badRequest("Cannot confirm receipt for inactive store");
                }
                StoreStockBalance balance = storeStockBalanceRepository
                        .findForUpdate(storeId, itemId)
                        .orElseGet(() -> {
                            StoreStockBalance newBalance = new StoreStockBalance();
                            newBalance.setStockItem(stockItem);
                            newBalance.setStoreLocation(store);
                            newBalance.setQuantity(ZERO);
                            return newBalance;
                        });
                BigDecimal current = balance.getQuantity();
                if (current == null || current.compareTo(ZERO) < 0
                        || !fitsQuantityColumn(current)) {
                    throw conflict("Invalid store stock balance");
                }
                BigDecimal updated = current.add(allocation.getAllocatedQuantity());
                if (!fitsQuantityColumn(updated)) {
                    throw badRequest("Store stock quantity exceeds supported range");
                }
                balance.setQuantity(updated);
                storeStockBalanceRepository.saveAndFlush(balance);
            }

            // Maintain the legacy aggregate while other modules still read it.
            stockItem.setCurrentQuantity(after);
            stockItemRepository.save(stockItem);

            StockTransaction transaction = new StockTransaction();
            transaction.setStockItem(stockItem);
            transaction.setTransactionType(StockTransactionType.STOCK_IN);
            transaction.setQuantity(received);
            transaction.setQuantityBefore(before);
            transaction.setQuantityAfter(after);
            transaction.setReferenceType("GRN");
            transaction.setReferenceId(grn.getId());
            transaction.setReason("Stock received via " + grn.getGrnNumber());
            transactionRepository.save(transaction);
        }

        grn.setStatus(GRNStatus.CONFIRMED);
        grn.setReceivedAt(LocalDateTime.now());
        grnRepository.saveAndFlush(grn);
        return mapToResponse(grn);
    }

    @Transactional
    public GRNResponse cancelGRN(Long id) {
        GRN grn = grnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("GRN not found"));
        if (grn.getStatus() != GRNStatus.DRAFT) {
            throw conflict("Only DRAFT GRNs can be cancelled");
        }
        grn.setStatus(GRNStatus.CANCELLED);
        GRN saved = grnRepository.saveAndFlush(grn);
        // DRAFT never changed any stock, so there is nothing to roll back.
        return mapToResponse(saved);
    }

    private void validateQuantityForUnit(StockItem item, BigDecimal quantity) {
        if (item == null || !isValidQuantity(quantity)) {
            throw badRequest("Invalid stock item or quantity");
        }
        String unit = item.getUnit();
        if ("PCS".equals(unit)) {
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw badRequest("PCS items require whole-number quantities");
            }
        } else if (!"L".equals(unit) && !"ML".equals(unit)) {
            throw conflict("Stock item has an invalid measurement unit");
        }
    }

    private boolean isValidQuantity(BigDecimal quantity) {
        return quantity != null && quantity.compareTo(ZERO) > 0
                && fitsQuantityColumn(quantity);
    }

    private boolean fitsQuantityColumn(BigDecimal quantity) {
        if (quantity == null) return false;
        BigDecimal normalized = quantity.stripTrailingZeros();
        int fractionDigits = Math.max(0, normalized.scale());
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        return fractionDigits <= 3 && integerDigits <= 11;
    }

    private boolean isValidUnitCost(BigDecimal unitCost) {
        if (unitCost == null || unitCost.compareTo(ZERO) < 0) return false;
        BigDecimal normalized = unitCost.stripTrailingZeros();
        int fractionDigits = Math.max(0, normalized.scale());
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        return fractionDigits <= 2 && integerDigits <= 10;
    }

    private GRNResponse mapToResponse(GRN grn) {
        GRNResponse response = new GRNResponse();
        response.setId(grn.getId());
        response.setGrnNumber(grn.getGrnNumber());
        response.setSupplierId(grn.getSupplier().getId());
        response.setStatus(grn.getStatus());
        response.setReceivedAt(grn.getReceivedAt());
        response.setCreatedAt(grn.getCreatedAt());
        response.setNotes(grn.getNotes());
        response.setSupplierName(grn.getSupplier().getSupplierName());

        List<GRNItemResponse> itemResponses = grn.getItems().stream()
                .map(this::mapItemToResponse).toList();
        response.setItems(itemResponses);
        response.setTotalAmount(itemResponses.stream()
                .map(GRNItemResponse::getLineTotal)
                .reduce(ZERO, BigDecimal::add));
        return response;
    }

    private GRNItemResponse mapItemToResponse(GRNItem item) {
        GRNItemResponse response = new GRNItemResponse();
        response.setId(item.getId());
        response.setStockItemId(item.getStockItem().getId());
        response.setItemCode(item.getStockItem().getItemCode());
        response.setItemName(item.getStockItem().getItemName());
        response.setReceivedQuantity(item.getReceivedQuantity());
        response.setUnitCost(item.getUnitCost());
        response.setLineTotal(item.getUnitCost().multiply(item.getReceivedQuantity()));
        response.setAllocations(item.getAllocations().stream()
                .sorted(Comparator.comparing(a -> a.getStoreLocation().getId()))
                .map(a -> new GRNItemResponse.StoreAllocationResponse(
                        a.getStoreLocation().getId(),
                        a.getStoreLocation().getStoreCode(),
                        a.getStoreLocation().getStoreName(),
                        a.getAllocatedQuantity()))
                .toList());
        return response;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
