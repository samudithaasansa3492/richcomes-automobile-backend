package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.service.dto.ServiceCategoryDTO;
import com.richcomes.garage.service.entity.ServiceCategory;
import com.richcomes.garage.service.repository.ServiceCategoryRepository;
import com.richcomes.garage.service.service.ServiceCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceCategoryServiceImpl implements ServiceCategoryService {

    private final ServiceCategoryRepository repository;

    @Autowired
    public ServiceCategoryServiceImpl(ServiceCategoryRepository repository) {
        this.repository = repository;
    }

    // Helper Method: Entity එක DTO එකකට හැරවීම (Mapping)
    private ServiceCategoryDTO convertToDTO(ServiceCategory entity) {
        ServiceCategoryDTO dto = new ServiceCategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    // Helper Method: DTO එක Entity එකකට හැරවීම (Database එකට Save කිරීමට පෙර)
    private ServiceCategory convertToEntity(ServiceCategoryDTO dto) {
        ServiceCategory entity = new ServiceCategory();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive());
        return entity;
    }

    @Override
    public ServiceCategoryDTO createCategory(ServiceCategoryDTO categoryDTO) {
        ServiceCategory entity = convertToEntity(categoryDTO);
        ServiceCategory savedEntity = repository.save(entity);
        return convertToDTO(savedEntity);
    }

    @Override
    public List<ServiceCategoryDTO> getAllCategories() {
        List<ServiceCategory> entities = repository.findAll();
        // සියලුම Entities ටික DTO ලැයිස්තුවකට හරවා යැවීම
        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceCategoryDTO getCategoryById(Long id) {
        ServiceCategory entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return convertToDTO(entity);
    }

    @Override
    public ServiceCategoryDTO updateCategory(Long id, ServiceCategoryDTO categoryDTO) {
        ServiceCategory existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        existing.setName(categoryDTO.getName());
        existing.setDescription(categoryDTO.getDescription());
        existing.setIsActive(categoryDTO.getIsActive());

        ServiceCategory updatedEntity = repository.save(existing);
        return convertToDTO(updatedEntity);
    }

    @Override
    public void deleteCategory(Long id) {
        repository.deleteById(id);
    }
}