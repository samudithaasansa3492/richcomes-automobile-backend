
package com.richcomes.garage.inventory.category.service;

import com.richcomes.garage.inventory.category.dto.request.StockCategoryRequest;
import com.richcomes.garage.inventory.category.dto.response.StockCategoryResponse;
import com.richcomes.garage.inventory.category.entity.StockCategory;
import com.richcomes.garage.inventory.category.repository.StockCategoryRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StockCategoryService {

    private final StockCategoryRepository repository;

    public StockCategoryService(StockCategoryRepository repository) {
        this.repository = repository;
    }

    // CREATE CATEGORY
    @Transactional
    public StockCategoryResponse createCategory(
            StockCategoryRequest request) {

        String name = request.getName().trim();

        if (repository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category already exists"
            );
        }

        StockCategory category = new StockCategory();
        category.setName(name);
        category.setDescription(request.getDescription());

        StockCategory saved = repository.save(category);

        return mapToResponse(saved);
    }

    // GET ALL CATEGORIES
    @Transactional(readOnly = true)
    public List<StockCategoryResponse> getAllCategories() {

        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET CATEGORY BY ID
    @Transactional(readOnly = true)
    public StockCategoryResponse getCategoryById(Long id) {

        StockCategory category = findCategory(id);

        return mapToResponse(category);
    }

    // UPDATE CATEGORY
    @Transactional
    public StockCategoryResponse updateCategory(
            Long id,
            StockCategoryRequest request) {

        StockCategory category = findCategory(id);
        String name = request.getName().trim();

        repository.findByNameIgnoreCase(name)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Category name already exists"
                        );
                    }
                });

        category.setName(name);
        category.setDescription(request.getDescription());

        return mapToResponse(repository.save(category));
    }

    // DEACTIVATE CATEGORY
    @Transactional
    public void deactivateCategory(Long id) {

        StockCategory category = findCategory(id);
        category.setActive(false);

        repository.save(category);
    }

    // ACTIVATE CATEGORY
    @Transactional
    public void activateCategory(Long id) {

        StockCategory category = findCategory(id);
        category.setActive(true);

        repository.save(category);
    }

    // FIND CATEGORY

    private StockCategory findCategory(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid category ID is required"
            );
        }

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Category not found"
                        )
                );
    }

    // ENTITY -> RESPONSE DTO
    private StockCategoryResponse mapToResponse(
            StockCategory category) {

        return new StockCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive()
        );
    }
}
