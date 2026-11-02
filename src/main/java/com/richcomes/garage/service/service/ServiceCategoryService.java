package com.richcomes.garage.service.service;

import com.richcomes.garage.service.dto.ServiceCategoryDTO;
import java.util.List;

public interface ServiceCategoryService {
    ServiceCategoryDTO createCategory(ServiceCategoryDTO categoryDTO);
    List<ServiceCategoryDTO> getAllCategories();
    ServiceCategoryDTO getCategoryById(Long id);
    ServiceCategoryDTO updateCategory(Long id, ServiceCategoryDTO categoryDTO);
    void deleteCategory(Long id);
}