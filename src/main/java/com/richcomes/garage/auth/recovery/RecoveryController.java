package com.richcomes.garage.auth.recovery;

import com.richcomes.garage.auth.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class RecoveryController {
    private final RecoveryService service;
    public RecoveryController(RecoveryService service) { this.service = service; }
    public record Enrollment(@NotBlank @Size(max=72) String password,
                             @NotBlank @Email @Size(max=254) String email) {
        @Override public String toString() { return "Enrollment[REDACTED]"; }
    }
    public record Request(@NotBlank @Size(max=80) String username) {}
    public record Token(@NotBlank @Size(max=43) String token) {
        @Override public String toString() { return "Token[REDACTED]"; }
    }
    public record Reset(@NotBlank @Size(max=43) String token,
                        @NotBlank @Size(min=15,max=72) String password) {
        @Override public String toString() { return "Reset[REDACTED]"; }
    }
    @PostMapping("/api/auth/recovery/email")
    public ResponseEntity<Void> enroll(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Enrollment input) {
        service.enroll(actor.accountId(), input.password(), input.email()); return accepted();
    }
    @PostMapping("/api/auth/recovery/email/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody Token input) {
        service.verifyEmail(input.token()); return accepted();
    }
    @PostMapping("/api/auth/recovery/request")
    public ResponseEntity<Void> request(@Valid @RequestBody Request input) {
        try { service.request(input.username(), null); } catch (RecoveryFailure ignored) { /* Generic response. */ }
        return accepted();
    }
    @PostMapping("/api/technical/recovery/request")
    public ResponseEntity<Void> supportRequest(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Request input) {
        try { service.request(input.username(), actor.accountId()); } catch (RecoveryFailure ignored) { /* Generic response. */ }
        return accepted();
    }
    @PostMapping("/api/auth/recovery/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody Reset input) {
        service.reset(input.token(), input.password()); return accepted();
    }
    private ResponseEntity<Void> accepted() {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }
}
