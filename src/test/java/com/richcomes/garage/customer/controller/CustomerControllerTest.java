package com.richcomes.garage.customer.controller;

import com.richcomes.garage.customer.dto.CustomerResponse;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.enums.CustomerType;
import com.richcomes.garage.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void createCustomerReturnsCreatedCustomer() throws Exception {
        when(customerService.createCustomer(org.mockito.ArgumentMatchers.any()))
                .thenReturn(customer(1L, CustomerStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerType": "PERSONAL",
                                  "name": "Alex Customer",
                                  "nic": "123456789V",
                                  "address": "10 Main Street",
                                  "mobile": "0771234567",
                                  "email": "alex@example.com",
                                  "city": "Colombo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alex Customer"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createCustomerRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerType": "PERSONAL",
                                  "name": "",
                                  "nic": "invalid",
                                  "address": "",
                                  "mobile": "",
                                  "email": "not-an-email",
                                  "city": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService);
    }

    @Test
    void getAllCustomersReturnsCustomerList() throws Exception {
        when(customerService.getAllCustomers())
                .thenReturn(List.of(customer(1L, CustomerStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Alex Customer"));
    }

    @Test
    void getCustomerByIdReturnsCustomer() throws Exception {
        when(customerService.getCustomerById(1L))
                .thenReturn(customer(1L, CustomerStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getCustomerByNicReturnsCustomer() throws Exception {
        when(customerService.getCustomerByNic("123456789V"))
                .thenReturn(customer(1L, CustomerStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/customers/nic/123456789V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nic").value("123456789V"));
    }

    @Test
    void searchCustomersByNameReturnsMatches() throws Exception {
        when(customerService.searchCustomersByName("Alex"))
                .thenReturn(List.of(customer(1L, CustomerStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/customers/search").param("name", "Alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alex Customer"));
    }

    @Test
    void updateCustomerReturnsUpdatedCustomer() throws Exception {
        when(customerService.updateCustomer(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(customer(1L, CustomerStatus.ACTIVE));

        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Alex Customer",
                                  "email": "alex@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateCustomerRejectsInvalidRequest() throws Exception {
        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService);
    }

    @Test
    void suspendCustomerReturnsSuspendedCustomer() throws Exception {
        when(customerService.suspendCustomer(1L))
                .thenReturn(customer(1L, CustomerStatus.SUSPENDED));

        mockMvc.perform(patch("/api/v1/customers/1/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void activateCustomerReturnsActiveCustomer() throws Exception {
        when(customerService.activateCustomer(1L))
                .thenReturn(customer(1L, CustomerStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/customers/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(customerService).activateCustomer(1L);
    }

    private CustomerResponse customer(Long id, CustomerStatus status) {
        CustomerResponse response = new CustomerResponse();
        response.setId(id);
        response.setCustomerType(CustomerType.PERSONAL);
        response.setName("Alex Customer");
        response.setNic("123456789V");
        response.setStatus(status);
        return response;
    }
}
