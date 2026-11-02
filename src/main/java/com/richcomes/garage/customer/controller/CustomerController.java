package com.richcomes.garage.customer.controller;

import com.richcomes.garage.customer.dto.CustomerCreateRequest;
import com.richcomes.garage.customer.dto.CustomerResponse;
import com.richcomes.garage.customer.dto.CustomerUpdateRequest;
import com.richcomes.garage.customer.service.CustomerService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CustomerCreateRequest request) {

        CustomerResponse response =
                customerService.createCustomer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {

        return ResponseEntity.ok(
                customerService.getAllCustomers()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                customerService.getCustomerById(id)
        );
    }

    @GetMapping("/nic/{nic}")
    public ResponseEntity<CustomerResponse> getCustomerByNic(
            @PathVariable String nic) {

        return ResponseEntity.ok(
                customerService.getCustomerByNic(nic)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<CustomerResponse>> searchByName(
            @RequestParam String name) {

        return ResponseEntity.ok(
                customerService.searchCustomersByName(name)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request) {

        return ResponseEntity.ok(
                customerService.updateCustomer(id, request)
        );
    }

    @PatchMapping("/{id}/suspend")
    public ResponseEntity<CustomerResponse> suspendCustomer(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                customerService.suspendCustomer(id)
        );
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                customerService.activateCustomer(id)
        );
    }
}