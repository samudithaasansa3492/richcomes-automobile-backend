package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.dto.CustomerProfileResponse;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.customer.repository.CustomerRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** No user-supplied customer ID: always resolve profile from the validated JWT principal. */
@RestController
public class CustomerMeController {
    private final CustomerRepository customers;

    public CustomerMeController(CustomerRepository customers) {
        this.customers = customers;
    }

    @GetMapping("/api/customer/me")
    public ResponseEntity<CustomerProfileResponse> me(@AuthenticationPrincipal AccountPrincipal principal) {
        if (principal == null || principal.customerId() == null
                || !principal.roles().equals(java.util.Set.of("CUSTOMER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer identity required");
        }
        var customer = customers.findById(principal.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CustomerProfileResponse(customer.getId(), customer.getName(),
                        customer.getEmail(), customer.getMobile(), customer.getCity()));
    }
}
