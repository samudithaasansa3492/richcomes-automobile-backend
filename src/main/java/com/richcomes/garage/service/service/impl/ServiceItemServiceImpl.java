package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.service.dto.ServiceItemDTO;
import com.richcomes.garage.service.entity.ServiceCategory;
import com.richcomes.garage.service.entity.ServiceItem;
import com.richcomes.garage.service.repository.ServiceCategoryRepository;
import com.richcomes.garage.service.repository.ServiceItemRepository;
import com.richcomes.garage.service.service.ServiceItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceItemServiceImpl implements ServiceItemService {

    private final ServiceItemRepository repository;
    private final ServiceCategoryRepository categoryRepository;

    @Autowired
    public ServiceItemServiceImpl(ServiceItemRepository repository, ServiceCategoryRepository categoryRepository) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
    }

    // Helper Method: Entity එක DTO එකකට හැරවීම
    private ServiceItemDTO convertToDTO(ServiceItem entity) {
        ServiceItemDTO dto = new ServiceItemDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStandardLabourHours(entity.getStandardLabourHours());
        dto.setDefaultLabourRate(entity.getDefaultLabourRate());
        dto.setIsActive(entity.getIsActive());

        if (entity.getCategory() != null) {
            dto.setCategoryId(entity.getCategory().getId());
            dto.setCategoryName(entity.getCategory().getName());
        }
        return dto;
    }

    // Helper Method: DTO එක Entity එකකට හැරවීම
    private ServiceItem convertToEntity(ServiceItemDTO dto) {
        ServiceItem entity = new ServiceItem();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStandardLabourHours(dto.getStandardLabourHours());
        entity.setDefaultLabourRate(dto.getDefaultLabourRate());
        entity.setIsActive(dto.getIsActive());

        if (dto.getCategoryId() != null) {
            ServiceCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getCategoryId()));
            entity.setCategory(category);
        }
        return entity;
    }

    @Override
    public ServiceItemDTO createServiceItem(ServiceItemDTO serviceItemDTO) {
        ServiceItem entity = convertToEntity(serviceItemDTO);
        ServiceItem savedEntity = repository.save(entity);
        return convertToDTO(savedEntity);
    }

    @Override
    public List<ServiceItemDTO> getAllServiceItems() {
        return repository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceItemDTO getServiceItemById(Long id) {
        ServiceItem entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service Item not found with id: " + id));
        return convertToDTO(entity);
    }

    @Override
    public ServiceItemDTO updateServiceItem(Long id, ServiceItemDTO updatedDTO) {
        ServiceItem existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service Item not found with id: " + id));

        existing.setName(updatedDTO.getName());
        existing.setDescription(updatedDTO.getDescription());
        existing.setStandardLabourHours(updatedDTO.getStandardLabourHours());
        existing.setDefaultLabourRate(updatedDTO.getDefaultLabourRate());
        existing.setIsActive(updatedDTO.getIsActive());

        if (updatedDTO.getCategoryId() != null) {
            ServiceCategory category = categoryRepository.findById(updatedDTO.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + updatedDTO.getCategoryId()));
            existing.setCategory(category);
        }

        ServiceItem savedEntity = repository.save(existing);
        return convertToDTO(savedEntity);
    }

    @Override
    public void deleteServiceItem(Long id) {
        repository.deleteById(id);
    }

    @Override
    public List<ServiceItemDTO> getItemsByCategoryId(Long categoryId) {
        return repository.findByCategoryId(categoryId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
}