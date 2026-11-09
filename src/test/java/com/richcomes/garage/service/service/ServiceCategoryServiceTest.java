package com.richcomes.garage.service.service;

import com.richcomes.garage.service.dto.ServiceCategoryDTO;
import com.richcomes.garage.service.entity.ServiceCategory;
import com.richcomes.garage.service.repository.ServiceCategoryRepository;
import com.richcomes.garage.service.service.impl.ServiceCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCategoryServiceTest {

    @Mock
    private ServiceCategoryRepository categoryRepository;

    @InjectMocks
    private ServiceCategoryServiceImpl categoryService; // මෙහි ServiceCategoryService ලෙස වෙනස් කිරීමට හැක

    @Test
    void testFindById() {
        ServiceCategory category = new ServiceCategory();
        category.setId(1L);
        category.setName("Repair");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        ServiceCategoryDTO found = categoryService.getCategoryById(1L);

        assertNotNull(found);
        assertEquals("Repair", found.getName());
        verify(categoryRepository, times(1)).findById(1L);
    }
}