
package com.richcomes.garage.inventory.grn.service;

import com.richcomes.garage.inventory.grn.dto.request.GRNItemRequest;
import com.richcomes.garage.inventory.grn.dto.request.GRNRequest;
import com.richcomes.garage.inventory.grn.dto.response.GRNItemResponse;
import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.entity.GRN;
import com.richcomes.garage.inventory.grn.entity.GRNItem;
import com.richcomes.garage.inventory.grn.enums.GRNStatus;
import com.richcomes.garage.inventory.grn.repository.GRNRepository;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
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

    public GRNService(
            GRNRepository grnRepository,
            SupplierRepository supplierRepository,
            StockItemRepository stockItemRepository,
            StockTransactionRepository transactionRepository) {

        this.grnRepository = grnRepository;
        this.supplierRepository = supplierRepository;
        this.stockItemRepository = stockItemRepository;
        this.transactionRepository = transactionRepository;
    }

    // CREATE DRAFT GRN
    @Transactional
    public GRNResponse createDraft(GRNRequest request) {

        if (request == null
                || request.getSupplierId() == null
                || request.getItems() == null
                || request.getItems().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supplier and at least one item are required"
            );
        }

        Supplier supplier = supplierRepository
                .findById(request.getSupplierId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        if (!supplier.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive supplier cannot be used for a GRN"
            );
        }

        GRN grn = new GRN();
        grn.setSupplier(supplier);

        if (request.getNotes() != null
                && request.getNotes().length() > 500) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "GRN notes cannot exceed 500 characters"
            );
        }

        grn.setNotes(request.getNotes());

        // Database UNIQUE constraint remains necessary.
        grn.setGrnNumber(
                "GRN-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase()
        );

        Set<Long> itemIds = new HashSet<>();

        for (GRNItemRequest itemRequest : request.getItems()) {

            if (itemRequest == null
                    || itemRequest.getStockItemId() == null
                    || !isValidQuantity(
                    itemRequest.getReceivedQuantity()
            )
                    || !isValidUnitCost(
                    itemRequest.getUnitCost()
            )) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid GRN item quantity or unit cost"
                );
            }

            if (!itemIds.add(itemRequest.getStockItemId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate stock item in GRN"
                );
            }

            StockItem stockItem = stockItemRepository
                    .findById(itemRequest.getStockItemId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Stock item not found"
                    ));

            if (!stockItem.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Inactive stock item cannot be received"
                );
            }

            // NEW: Validate quantity against item's unit.
            validateQuantityForUnit(
                    stockItem,
                    itemRequest.getReceivedQuantity()
            );

            GRNItem grnItem = new GRNItem();

            grnItem.setStockItem(stockItem);
            grnItem.setReceivedQuantity(
                    itemRequest.getReceivedQuantity()
            );
            grnItem.setUnitCost(itemRequest.getUnitCost());

            grn.addItem(grnItem);
        }

        GRN saved = grnRepository.saveAndFlush(grn);

        // DRAFT creation does not increase stock.
        return mapToResponse(saved);
    }

    // GET ALL GRNS
    @Transactional(readOnly = true)
    public List<GRNResponse> getAllGRNs() {

        return grnRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET ONE GRN
    @Transactional(readOnly = true)
    public GRNResponse getGRNById(Long id) {

        GRN grn = grnRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GRN not found"
                ));

        return mapToResponse(grn);
    }

    // CONFIRM GRN AND INCREASE STOCK
    @Transactional
    public GRNResponse confirmGRN(Long id) {

        GRN grn = grnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GRN not found"
                ));

        // Prevent duplicate confirmation.
        if (grn.getStatus() != GRNStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT GRNs can be confirmed"
            );
        }

        if (grn.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot confirm an empty GRN"
            );
        }

        // Consistent lock ordering reduces deadlock risk.
        List<GRNItem> sortedItems = grn.getItems()
                .stream()
                .sorted(Comparator.comparing(
                        item -> item.getStockItem().getId()
                ))
                .toList();

        for (GRNItem grnItem : sortedItems) {

            Long stockItemId = grnItem.getStockItem().getId();

            StockItem stockItem = stockItemRepository
                    .findByIdForUpdate(stockItemId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Stock item not found: " + stockItemId
                    ));

            if (!stockItem.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot receive inactive stock item"
                );
            }

            BigDecimal received =
                    grnItem.getReceivedQuantity();

            if (!isValidQuantity(received)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid received quantity"
                );
            }

            // NEW: Recheck unit at confirmation time.
            // This also protects existing DRAFT records.
            validateQuantityForUnit(stockItem, received);

            BigDecimal before =
                    stockItem.getCurrentQuantity();

            if (before == null
                    || before.compareTo(ZERO) < 0
                    || !fitsQuantityColumn(before)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Invalid current stock quantity"
                );
            }

            BigDecimal after = before.add(received);

            if (!fitsQuantityColumn(after)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Stock quantity exceeds supported range"
                );
            }

            stockItem.setCurrentQuantity(after);
            stockItemRepository.save(stockItem);

            // Record STOCK_IN transaction.
            StockTransaction transaction =
                    new StockTransaction();

            transaction.setStockItem(stockItem);
            transaction.setTransactionType(
                    StockTransactionType.STOCK_IN
            );

            transaction.setQuantity(received);
            transaction.setQuantityBefore(before);
            transaction.setQuantityAfter(after);

            transaction.setReferenceType("GRN");
            transaction.setReferenceId(grn.getId());

            transaction.setReason(
                    "Stock received via " + grn.getGrnNumber()
            );

            transactionRepository.save(transaction);
        }

        grn.setStatus(GRNStatus.CONFIRMED);
        grn.setReceivedAt(LocalDateTime.now());

        grnRepository.saveAndFlush(grn);

        return mapToResponse(grn);
    }

    // CANCEL DRAFT GRN
    @Transactional
    public GRNResponse cancelGRN(Long id) {

        GRN grn = grnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GRN not found"
                ));

        if (grn.getStatus() != GRNStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT GRNs can be cancelled"
            );
        }

        grn.setStatus(GRNStatus.CANCELLED);

        GRN saved = grnRepository.saveAndFlush(grn);

        // Cancelling a DRAFT does not change stock.
        return mapToResponse(saved);
    }

    // NEW: VALIDATE QUANTITY AGAINST ITEM UNIT
    private void validateQuantityForUnit(
            StockItem stockItem,
            BigDecimal quantity) {

        if (stockItem == null || !isValidQuantity(quantity)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid stock item or quantity"
            );
        }

        String unit = stockItem.getUnit();

        if ("PCS".equals(unit)) {

            // 5 and 5.000 are whole numbers.
            // 1.5 is not a whole number.
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PCS items require whole-number quantities"
                );
            }

        } else if (!"L".equals(unit)
                && !"ML".equals(unit)) {

            // Reject unknown units rather than allowing
            // unvalidated stock movements.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock item has an invalid measurement unit"
            );
        }
    }

    // VALIDATE POSITIVE DECIMAL QUANTITY
    private boolean isValidQuantity(BigDecimal quantity) {

        return quantity != null
                && quantity.compareTo(ZERO) > 0
                && fitsQuantityColumn(quantity);
    }

    // VALIDATE NUMERIC(14,3)
    private boolean fitsQuantityColumn(BigDecimal quantity) {

        if (quantity == null) {
            return false;
        }

        BigDecimal normalized =
                quantity.stripTrailingZeros();

        int fractionDigits =
                Math.max(0, normalized.scale());

        int integerDigits =
                Math.max(
                        0,
                        normalized.precision() - normalized.scale()
                );

        return fractionDigits <= 3
                && integerDigits <= 11;
    }

    // VALIDATE NUMERIC(12,2) UNIT COST
    private boolean isValidUnitCost(BigDecimal unitCost) {

        if (unitCost == null
                || unitCost.compareTo(ZERO) < 0) {
            return false;
        }

        BigDecimal normalized =
                unitCost.stripTrailingZeros();

        int fractionDigits =
                Math.max(0, normalized.scale());

        int integerDigits =
                Math.max(
                        0,
                        normalized.precision() - normalized.scale()
                );

        return fractionDigits <= 2
                && integerDigits <= 10;
    }

    // ENTITY TO RESPONSE DTO
    private GRNResponse mapToResponse(GRN grn) {

        GRNResponse response = new GRNResponse();

        response.setId(grn.getId());
        response.setGrnNumber(grn.getGrnNumber());
        response.setSupplierId(grn.getSupplier().getId());
        response.setStatus(grn.getStatus());
        response.setReceivedAt(grn.getReceivedAt());
        response.setCreatedAt(grn.getCreatedAt());
        response.setNotes(grn.getNotes());

        response.setSupplierName(
                grn.getSupplier().getSupplierName()
        );

        List<GRNItemResponse> itemResponses =
                grn.getItems()
                        .stream()
                        .map(this::mapItemToResponse)
                        .toList();

        response.setItems(itemResponses);

        BigDecimal total = itemResponses.stream()
                .map(GRNItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        response.setTotalAmount(total);

        return response;
    }

    // GRN ITEM ENTITY TO RESPONSE DTO
    private GRNItemResponse mapItemToResponse(GRNItem item) {

        GRNItemResponse response = new GRNItemResponse();

        response.setId(item.getId());
        response.setStockItemId(
                item.getStockItem().getId()
        );
        response.setItemCode(
                item.getStockItem().getItemCode()
        );
        response.setItemName(
                item.getStockItem().getItemName()
        );

        response.setReceivedQuantity(
                item.getReceivedQuantity()
        );

        response.setUnitCost(item.getUnitCost());

        BigDecimal lineTotal = item.getUnitCost()
                .multiply(item.getReceivedQuantity());

        response.setLineTotal(lineTotal);

        return response;
    }
}
