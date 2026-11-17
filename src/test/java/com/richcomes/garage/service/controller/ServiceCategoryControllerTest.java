package com.richcomes.garage.service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full application security integration. Unauthenticated category access must
 * not be made public just to preserve an old pre-authentication 200 assertion.
 * Authorized controller behavior is covered in ServiceCategoryControllerSliceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceCategoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCategoryListingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/service-categories")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void invalidBearerCannotReadCategories() throws Exception {
        mockMvc.perform(get("/api/service-categories")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
