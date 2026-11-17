package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.dto.CashierPermissionChangeRequest;
import com.richcomes.garage.auth.dto.CashierPermissionsResponse;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.CashierPermissionService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Read or change exact per-account CASHIER business grants; OWNER only. */
@RestController
@RequestMapping("/api/accounts")
public class CashierPermissionController {
    private final CashierPermissionService service;

    public CashierPermissionController(CashierPermissionService service) {
        this.service = service;
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<CashierPermissionsResponse> list(
            @PathVariable Long id, @AuthenticationPrincipal AccountPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(id, actor));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<CashierPermissionsResponse> change(
            @PathVariable Long id,
            @Valid @RequestBody CashierPermissionChangeRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.change(id, request, actor));
    }
}
