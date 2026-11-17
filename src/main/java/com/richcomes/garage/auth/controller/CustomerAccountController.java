package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.dto.CustomerAccountCreateRequest;
import com.richcomes.garage.auth.dto.CustomerAccountResponse;
import com.richcomes.garage.auth.service.CustomerAccountService;
import com.richcomes.garage.auth.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Staff-only enrollment and separate identity-checked activation. */
@RestController
@RequestMapping("/api/customer-accounts")
public class CustomerAccountController {
    private final CustomerAccountService accounts;

    public CustomerAccountController(CustomerAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<CustomerAccountResponse> register(
            @Valid @RequestBody CustomerAccountCreateRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        var created = accounts.register(request, actor);
        return ResponseEntity.created(URI.create("/api/customer-accounts/" + created.accountId()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<CustomerAccountResponse> activate(@PathVariable Long id,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.activate(id, actor));
    }
}
