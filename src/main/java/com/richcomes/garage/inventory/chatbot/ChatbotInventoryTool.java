
package com.richcomes.garage.inventory.chatbot;

import com.richcomes.garage.inventory.stockitem.dto.responce.StockItemResponse;
import com.richcomes.garage.inventory.stockitem.service.StockItemService;

import com.richcomes.garage.inventory.supplier.dto.responce.SupplierResponse;
import com.richcomes.garage.inventory.supplier.service.SupplierService;

import com.richcomes.garage.inventory.category.dto.response.StockCategoryResponse;
import com.richcomes.garage.inventory.category.service.StockCategoryService;

import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.service.GRNService;

import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.service.StockMovementService;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ChatbotInventoryTool {

    private final StockItemService stockItemService;
    private final SupplierService supplierService;
    private final StockCategoryService categoryService;
    private final GRNService grnService;
    private final StockMovementService stockMovementService;

    public ChatbotInventoryTool(
            StockItemService stockItemService,
            SupplierService supplierService,
            StockCategoryService categoryService,
            GRNService grnService,
            StockMovementService stockMovementService) {

        this.stockItemService = stockItemService;
        this.supplierService = supplierService;
        this.categoryService = categoryService;
        this.grnService = grnService;
        this.stockMovementService = stockMovementService;
    }

    // TOOL 1: GET LOW-STOCK ITEMS
    public List<LowStockItem> getLowStockItems() {

        return stockItemService.getLowStockItems()
                .stream()
                .limit(20)
                .map(this::toLowStockItem)
                .toList();
    }

    // TOOL 2: FIND STOCK ITEM BY CODE
    public Optional<StockItemDetail> getStockItemByCode(
            String itemCode) {

        if (itemCode == null
                || itemCode.isBlank()
                || itemCode.length() > 100) {

            throw new IllegalArgumentException(
                    "Valid item code is required"
            );
        }

        try {
            StockItemResponse item =
                    stockItemService.getItemByCode(itemCode.trim());

            return Optional.of(toStockItemDetail(item));

        } catch (ResponseStatusException e) {

            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }

            throw e;
        }
    }

    // TOOL 3: SEARCH STOCK ITEMS BY NAME
    public List<StockItemDetail> searchStockItemsByName(
            String itemName) {

        if (itemName == null
                || itemName.isBlank()
                || itemName.trim().length() < 2
                || itemName.length() > 100) {

            throw new IllegalArgumentException(
                    "Item name must contain 2-100 characters"
            );
        }

        return stockItemService
                .searchItemsByName(itemName.trim())
                .stream()
                .limit(20)
                .map(this::toStockItemDetail)
                .toList();
    }

    // TOOL 4: GET SUPPLIERS
    public List<SupplierInfo> getSuppliers() {

        return supplierService.getAllSuppliers()
                .stream()
                .limit(20)
                .map(this::toSupplierInfo)
                .toList();
    }

    // TOOL 5: GET STOCK CATEGORIES
    public List<CategoryInfo> getCategories() {

        return categoryService.getAllCategories()
                .stream()
                .limit(20)
                .map(this::toCategoryInfo)
                .toList();
    }

    // TOOL 6: GET RECENT GRNS
    public List<GRNSummary> getRecentGRNs() {

        return grnService.getAllGRNs()
                .stream()
                .limit(20)
                .map(this::toGRNSummary)
                .toList();
    }

    // TOOL 7: GET RECENT STOCK MOVEMENTS
    // Database-level pagination
    public List<StockMovementSummary> getRecentStockMovements() {

        return stockMovementService
                .getRecentTransactions()
                .stream()
                .map(this::toStockMovementSummary)
                .toList();
    }

    // TOOL 8: GET STOCK MOVEMENTS BY TYPE
    public List<StockMovementSummary> getStockMovementsByType(
            String transactionType) {

        if (transactionType == null
                || transactionType.isBlank()) {

            throw new IllegalArgumentException(
                    "Transaction type is required"
            );
        }

        StockTransactionType type;

        try {
            type = StockTransactionType.valueOf(
                    transactionType.trim()
                            .toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Unsupported transaction type",
                    e
            );
        }

        return stockMovementService
                .getTransactionsByType(type)
                .stream()
                .map(this::toStockMovementSummary)
                .toList();
    }

    // MAP LOW-STOCK RESPONSE
    private LowStockItem toLowStockItem(
            StockItemResponse item) {

        return new LowStockItem(
                item.getItemCode(),
                item.getItemName(),
                String.valueOf(item.getCurrentQuantity()),
                String.valueOf(item.getReorderLevel())
        );
    }

    // MAP STOCK ITEM RESPONSE
    private StockItemDetail toStockItemDetail(
            StockItemResponse item) {

        return new StockItemDetail(
                item.getItemCode(),
                item.getItemName(),
                item.getCategoryName(),
                String.valueOf(item.getCurrentQuantity()),
                String.valueOf(item.getReorderLevel()),
                item.isActive()
        );
    }

    // MAP SUPPLIER RESPONSE
    private SupplierInfo toSupplierInfo(
            SupplierResponse supplier) {

        return new SupplierInfo(
                supplier.getId(),
                supplier.getSupplierName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.isActive()
        );
    }

    // MAP CATEGORY RESPONSE
    // Correct getter: isActive()
    private CategoryInfo toCategoryInfo(
            StockCategoryResponse category) {

        return new CategoryInfo(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive()
        );
    }

    // MAP GRN RESPONSE
    private GRNSummary toGRNSummary(
            GRNResponse grn) {

        return new GRNSummary(
                grn.getId(),
                grn.getGrnNumber(),
                grn.getSupplierName(),
                grn.getStatus() == null
                        ? null
                        : grn.getStatus().name(),
                grn.getCreatedAt() == null
                        ? null
                        : grn.getCreatedAt().toString(),
                grn.getReceivedAt() == null
                        ? null
                        : grn.getReceivedAt().toString()
        );
    }

    // MAP STOCK MOVEMENT RESPONSE
    private StockMovementSummary toStockMovementSummary(
            StockMovementResponse movement) {

        return new StockMovementSummary(
                movement.getId(),
                movement.getItemCode(),
                movement.getItemName(),
                movement.getTransactionType() == null
                        ? null
                        : movement.getTransactionType().name(),
                movement.getQuantity(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getCreatedAt() == null
                        ? null
                        : movement.getCreatedAt().toString()
        );
    }

    // LOW-STOCK DATA
    public record LowStockItem(
            String itemCode,
            String itemName,
            String currentQuantity,
            String reorderLevel
    ) {
    }

    // STOCK ITEM DATA
    public record StockItemDetail(
            String itemCode,
            String itemName,
            String categoryName,
            String currentQuantity,
            String reorderLevel,
            boolean active
    ) {
    }

    // SUPPLIER DATA
    public record SupplierInfo(
            Long id,
            String supplierName,
            String contactPerson,
            String phone,
            String email,
            boolean active
    ) {
    }

    // CATEGORY DATA
    public record CategoryInfo(
            Long id,
            String name,
            String description,
            boolean active
    ) {
    }

    // GRN DATA
    public record GRNSummary(
            Long id,
            String grnNumber,
            String supplierName,
            String status,
            String createdAt,
            String receivedAt
    ) {
    }

    // STOCK MOVEMENT DATA
    public record StockMovementSummary(
            Long id,
            String itemCode,
            String itemName,
            String transactionType,
            Integer quantity,
            Integer quantityBefore,
            Integer quantityAfter,
            String createdAt
    ) {
    }
}
