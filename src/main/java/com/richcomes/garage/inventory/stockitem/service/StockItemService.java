
package com.richcomes.garage.inventory.stockitem.service;

import com.richcomes.garage.inventory.stockitem.dto.request.StockItemRequest;
import com.richcomes.garage.inventory.stockitem.dto.responce.StockItemResponse;
import com.richcomes.garage.inventory.category.entity.StockCategory;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.category.repository.StockCategoryRepository;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class StockItemService {

    private final StockItemRepository itemRepository;
    private final StockCategoryRepository categoryRepository;

    public StockItemService(
            StockItemRepository itemRepository,
            StockCategoryRepository categoryRepository) {

        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
    }

    // CREATE STOCK ITEM
    @Transactional
    public StockItemResponse createItem(StockItemRequest request) {

        validateRequest(request);

        String itemCode = request.getItemCode().trim();
        String barcode = normalizeBarcode(request.getBarcode());

        if (itemRepository.existsByItemCode(itemCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Item code already exists"
            );
        }

        if (barcode != null
                && itemRepository.existsByBarcode(barcode)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Barcode already exists"
            );
        }

        StockCategory category =
                findActiveCategory(request.getCategoryId());

        StockItem item = new StockItem();

        applyRequest(item, request, category);

        // Stock is added only through GRN or stock movement.
        item.setCurrentQuantity(BigDecimal.ZERO);

        return mapToResponse(itemRepository.save(item));
    }

    // GET ALL ITEMS
    @Transactional(readOnly = true)
    public List<StockItemResponse> getAllItems() {

        return itemRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET ITEM BY ID
    @Transactional(readOnly = true)
    public StockItemResponse getItemById(Long id) {

        return mapToResponse(findItem(id));
    }

    // GET ITEMS BY CATEGORY
    @Transactional(readOnly = true)
    public List<StockItemResponse> getItemsByCategory(
            Long categoryId) {

        if (categoryId == null || categoryId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock category ID is required"
            );
        }

        return itemRepository.findByCategoryId(categoryId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET LOW STOCK ITEMS
    @Transactional(readOnly = true)
    public List<StockItemResponse> getLowStockItems() {

        return itemRepository.findByActiveTrue()
                .stream()
                .filter(this::isLowStock)
                .map(this::mapToResponse)
                .toList();
    }

    // GET ITEM BY CODE
    @Transactional(readOnly = true)
    public StockItemResponse getItemByCode(String itemCode) {

        if (itemCode == null || itemCode.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock item code is required"
            );
        }

        StockItem item = itemRepository
                .findByItemCode(itemCode.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Stock item not found"
                ));

        return mapToResponse(item);
    }

    // SEARCH ITEMS BY NAME
    @Transactional(readOnly = true)
    public List<StockItemResponse> searchItemsByName(
            String itemName) {

        if (itemName == null
                || itemName.isBlank()
                || itemName.trim().length() < 2) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item name must contain at least 2 characters"
            );
        }

        String searchText =
                itemName.trim().toLowerCase(Locale.ROOT);

        return itemRepository.findAll()
                .stream()
                .filter(item ->
                        item.getItemName() != null
                                && item.getItemName()
                                .toLowerCase(Locale.ROOT)
                                .contains(searchText)
                )
                .map(this::mapToResponse)
                .toList();
    }

    // UPDATE STOCK ITEM DETAILS
    @Transactional
    public StockItemResponse updateItem(
            Long id,
            StockItemRequest request) {

        validateRequest(request);

        StockItem item = findItem(id);

        String itemCode = request.getItemCode().trim();
        String barcode = normalizeBarcode(request.getBarcode());

        itemRepository.findByItemCode(itemCode)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Item code already exists"
                        );
                    }
                });

        if (barcode != null) {
            itemRepository.findByBarcode(barcode)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Barcode already exists"
                            );
                        }
                    });
        }

        String newUnit = request.getUnit();

        String existingUnit = item.getUnit();

        // Do not reinterpret existing stock quantities
        // by changing measurement units.
        if (existingUnit != null
                && !existingUnit.equals(newUnit)
                && item.getCurrentQuantity() != null
                && item.getCurrentQuantity()
                .compareTo(BigDecimal.ZERO) != 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot change unit while stock quantity is not zero"
            );
        }

        StockCategory category =
                findActiveCategory(request.getCategoryId());

        applyRequest(item, request, category);

        // Current quantity is NOT changed here.
        return mapToResponse(itemRepository.save(item));
    }

    // DEACTIVATE ITEM
    @Transactional
    public void deactivateItem(Long id) {

        StockItem item = findItem(id);
        item.setActive(false);
        itemRepository.save(item);
    }

    // ACTIVATE ITEM
    @Transactional
    public void activateItem(Long id) {

        StockItem item = findItem(id);

        if (!item.getCategory().isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot activate item with inactive category"
            );
        }

        item.setActive(true);
        itemRepository.save(item);
    }

    // VALIDATE ITEM REQUEST AND UNIT
    private void validateRequest(StockItemRequest request) {

        if (request == null
                || request.getItemCode() == null
                || request.getItemCode().isBlank()
                || request.getItemName() == null
                || request.getItemName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item code and item name are required"
            );
        }

        if (request.getItemCode().trim().length() > 50
                || request.getItemName().trim().length() > 150) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item code or item name exceeds maximum length"
            );
        }

        String unit = request.getUnit();

        if (!"PCS".equals(unit)
                && !"L".equals(unit)
                && !"ML".equals(unit)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unit must be PCS, L or ML"
            );
        }

        BigDecimal reorderLevel = request.getReorderLevel();

        if (reorderLevel == null
                || reorderLevel.compareTo(BigDecimal.ZERO) < 0
                || !fitsQuantityColumn(reorderLevel)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid reorder level"
            );
        }

        // PCS products cannot have fractional reorder levels.
        if ("PCS".equals(unit)
                && reorderLevel.stripTrailingZeros().scale() > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PCS reorder level must be a whole number"
            );
        }

        if (!isValidMoney(request.getUnitCost())
                || !isValidMoney(request.getSellingPrice())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid unit cost or selling price"
            );
        }

        if (request.getBarcode() != null
                && request.getBarcode().length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Barcode exceeds 100 characters"
            );
        }

        if (request.getDescription() != null
                && request.getDescription().length() > 500) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description exceeds 500 characters"
            );
        }

        if (request.getImageUrl() != null
                && request.getImageUrl().length() > 500) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image URL exceeds 500 characters"
            );
        }
    }

    // VALIDATE NUMERIC(14,3)
    private boolean fitsQuantityColumn(BigDecimal quantity) {

        if (quantity == null) {
            return false;
        }

        BigDecimal normalized = quantity.stripTrailingZeros();

        int fractionDigits = Math.max(0, normalized.scale());

        int integerDigits = Math.max(
                0,
                normalized.precision() - normalized.scale()
        );

        return fractionDigits <= 3 && integerDigits <= 11;
    }

    // VALIDATE NUMERIC(12,2)
    private boolean isValidMoney(BigDecimal amount) {

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        BigDecimal normalized = amount.stripTrailingZeros();

        int fractionDigits = Math.max(0, normalized.scale());

        int integerDigits = Math.max(
                0,
                normalized.precision() - normalized.scale()
        );

        return fractionDigits <= 2 && integerDigits <= 10;
    }

    // FIND ITEM
    private StockItem findItem(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock item ID is required"
            );
        }

        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Stock item not found"
                ));
    }

    // FIND ACTIVE CATEGORY
    private StockCategory findActiveCategory(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock category ID is required"
            );
        }

        StockCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Stock category not found"
                ));

        if (!category.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stock category is inactive"
            );
        }

        return category;
    }

    // APPLY STOCK ITEM REQUEST
    private void applyRequest(
            StockItem item,
            StockItemRequest request,
            StockCategory category) {

        item.setItemCode(request.getItemCode().trim());
        item.setItemName(request.getItemName().trim());
        item.setBarcode(normalizeBarcode(request.getBarcode()));
        item.setDescription(request.getDescription());
        item.setCategory(category);
        item.setImageUrl(request.getImageUrl());

        // NEW: Store measurement unit.
        item.setUnit(request.getUnit());

        item.setReorderLevel(request.getReorderLevel());
        item.setUnitCost(request.getUnitCost());
        item.setSellingPrice(request.getSellingPrice());
    }

    // NORMALIZE OPTIONAL BARCODE
    private String normalizeBarcode(String barcode) {

        if (barcode == null || barcode.isBlank()) {
            return null;
        }

        return barcode.trim();
    }

    // CHECK LOW STOCK
    private boolean isLowStock(StockItem item) {

        BigDecimal currentQuantity = item.getCurrentQuantity();
        BigDecimal reorderLevel = item.getReorderLevel();

        if (currentQuantity == null || reorderLevel == null) {
            return false;
        }

        return currentQuantity.compareTo(reorderLevel) <= 0;
    }

    // ENTITY TO RESPONSE DTO
    private StockItemResponse mapToResponse(StockItem item) {

        StockItemResponse response = new StockItemResponse();

        response.setId(item.getId());
        response.setItemCode(item.getItemCode());
        response.setItemName(item.getItemName());
        response.setBarcode(item.getBarcode());
        response.setDescription(item.getDescription());

        response.setCategoryId(item.getCategory().getId());
        response.setCategoryName(item.getCategory().getName());

        response.setImageUrl(item.getImageUrl());

        // NEW: Include unit in API response.
        response.setUnit(item.getUnit());

        response.setCurrentQuantity(item.getCurrentQuantity());
        response.setReorderLevel(item.getReorderLevel());

        response.setUnitCost(item.getUnitCost());
        response.setSellingPrice(item.getSellingPrice());

        response.setActive(item.isActive());
        response.setLowStock(item.isActive() && isLowStock(item));

        return response;
    }
}
