
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

import java.util.List;

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

        String itemCode = request.getItemCode().trim();
        String barcode = normalizeBarcode(request.getBarcode());

        if (itemRepository.existsByItemCode(itemCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Item code already exists"
            );
        }

        if (barcode != null &&
                itemRepository.existsByBarcode(barcode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Barcode already exists"
            );
        }

        StockCategory category = findActiveCategory(
                request.getCategoryId()
        );

        StockItem item = new StockItem();

        applyRequest(item, request, category);
        item.setCurrentQuantity(0);

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
                .filter(item ->
                        item.getCurrentQuantity()
                                <= item.getReorderLevel()
                )
                .map(this::mapToResponse)
                .toList();
    }

    // UPDATE STOCK ITEM DETAILS
    @Transactional
    public StockItemResponse updateItem(
            Long id,
            StockItemRequest request) {

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

        StockCategory category = findActiveCategory(
                request.getCategoryId()
        );

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

    // FIND ITEM
    private StockItem findItem(Long id) {

        return itemRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Stock item not found"
                        )
                );
    }

    // FIND ACTIVE CATEGORY
    private StockCategory findActiveCategory(Long id) {

        StockCategory category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Stock category not found"
                        )
                );

        if (!category.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stock category is inactive"
            );
        }

        return category;
    }

    // SET ITEM DETAILS
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
        response.setCurrentQuantity(item.getCurrentQuantity());
        response.setReorderLevel(item.getReorderLevel());

        response.setUnitCost(item.getUnitCost());
        response.setSellingPrice(item.getSellingPrice());

        response.setActive(item.isActive());

        response.setLowStock(
                item.isActive() &&
                        item.getCurrentQuantity() <= item.getReorderLevel()
        );

        return response;
    }
}
