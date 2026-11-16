package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.dto.AccountCreateRequest;
import com.richcomes.garage.auth.dto.AccountPageResponse;
import com.richcomes.garage.auth.dto.AccountResponse;
import com.richcomes.garage.auth.dto.AccountRolesUpdateRequest;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.UserAccountService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final UserAccountService accounts;

    public AccountController(UserAccountService accounts) { this.accounts = accounts; }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        var result = accounts.createAccount(request);
        return ResponseEntity.created(URI.create("/api/accounts/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping
    public ResponseEntity<AccountPageResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ok(accounts.listAccounts(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(@PathVariable Long id) { return ok(accounts.getAccount(id)); }

    @PutMapping("/{id}/roles")
    public ResponseEntity<AccountResponse> roles(@PathVariable Long id,
            @Valid @RequestBody AccountRolesUpdateRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return ok(accounts.replaceRoles(id, request, actor.accountId()));
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<AccountResponse> enable(@PathVariable Long id) { return ok(accounts.enableAccount(id)); }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<AccountResponse> disable(@PathVariable Long id,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return ok(accounts.disableAccount(id, actor.accountId()));
    }

    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
