package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.dto.LoginRequest;
import com.richcomes.garage.auth.dto.LoginResponse;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationService authentication;

    public AuthController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authentication.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AccountPrincipal> me(@AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(principal);
    }
}
