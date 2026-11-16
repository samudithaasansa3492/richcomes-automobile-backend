package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.dto.CustomerRegistrationDelegationRequest;
import com.richcomes.garage.auth.dto.CustomerRegistrationDelegationResponse;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.CustomerRegistrationDelegationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Only OWNER may delegate customer enrollment to a particular CASHIER. */
@RestController
@RequestMapping("/api/accounts")
public class CustomerRegistrationDelegationController {
    private final CustomerRegistrationDelegationService delegations;

    public CustomerRegistrationDelegationController(CustomerRegistrationDelegationService delegations) {
        this.delegations = delegations;
    }

    @PutMapping("/{id}/permissions/customer-registration")
    public ResponseEntity<CustomerRegistrationDelegationResponse> setAllowed(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRegistrationDelegationRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(delegations.setAllowed(id, request.enabled(), actor));
    }
}
