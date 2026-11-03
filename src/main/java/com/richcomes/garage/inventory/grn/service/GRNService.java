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

        if (request == null ||
                request.getSupplierId() == null ||
                request.getItems() == null ||
                request.getItems().isEmpty()) {
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

        GRN grn = new GRN();
        grn.setSupplier(supplier);
        if (request.getNotes() != null && request.getNotes().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "GRN notes cannot exceed 500 characters"
            );
        }
        grn.setNotes(request.getNotes());

        // Random unique-style number.
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

            if (itemRequest == null ||
                    itemRequest.getStockItemId() == null ||
                    itemRequest.getReceivedQuantity() == null ||
                    itemRequest.getReceivedQuantity() <= 0 ||
                    itemRequest.getUnitCost() == null ||
                    itemRequest.getUnitCost()
                            .compareTo(BigDecimal.ZERO) < 0 ||
                    itemRequest.getUnitCost().scale() > 2 ||
                    itemRequest.getUnitCost().precision() > 12) {

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

            GRNItem grnItem = new GRNItem();
            grnItem.setStockItem(stockItem);
            grnItem.setReceivedQuantity(
                    itemRequest.getReceivedQuantity()
            );
            grnItem.setUnitCost(itemRequest.getUnitCost());

            grn.addItem(grnItem);
        }

        GRN saved = grnRepository.saveAndFlush(grn);

        // Draft creation does NOT increase stock.
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

        response.setSupplierName(grn.getSupplier().getSupplierName());

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

    private GRNItemResponse mapItemToResponse(GRNItem item) {

        GRNItemResponse response = new GRNItemResponse();

        response.setId(item.getId());
        response.setStockItemId(item.getStockItem().getId());
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
                .multiply(
                        BigDecimal.valueOf(
                                item.getReceivedQuantity()
                        )
                );

        response.setLineTotal(lineTotal);

        return response;
    }

    // CONFIRM GRN AND INCREASE STOCK
    @Transactional
    public GRNResponse confirmGRN(Long id) {

        GRN grn = grnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GRN not found"
                ));

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

            Integer received = grnItem.getReceivedQuantity();

            if (received == null || received <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid received quantity"
                );
            }

            Integer before = stockItem.getCurrentQuantity();

            if (before == null || before < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Invalid current stock quantity"
                );
            }

            int after;

            try {
                after = Math.addExact(before, received);
            } catch (ArithmeticException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Stock quantity exceeds supported range"
                );
            }

            stockItem.setCurrentQuantity(after);
            stockItemRepository.save(stockItem);

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

        // Cancelling a draft does not change stock.
        return mapToResponse(saved);
    }

}

