package com.richcomes.garage.service.controller;

import com.richcomes.garage.auth.config.SecurityConfig;
import com.richcomes.garage.auth.security.AccountJwtAuthenticationConverter;
import com.richcomes.garage.service.service.ServiceCategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test controller mappings in isolation, not authorization.
 * Full-context security checks live in ServiceCategoryControllerTest.
 */
@WebMvcTest(value = ServiceCategoryController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, AccountJwtAuthenticationConverter.class}))
@AutoConfigureMockMvc(addFilters = false)
class ServiceCategoryControllerSliceTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ServiceCategoryService categoryService;

    @Test
    void categoryListingReturnsOkWithServiceData() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/service-categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(categoryService).getAllCategories();
    }
}
