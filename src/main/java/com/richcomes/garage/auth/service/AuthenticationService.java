package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.LoginRequest;
import com.richcomes.garage.auth.dto.LoginResponse;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AccessTokenService tokens;
    private final String dummyHash;

    public AuthenticationService(UserAccountRepository accounts, PasswordEncoder encoder, AccessTokenService tokens) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.password() == null || request.password().isBlank()
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badCredentials();
        }
        String username;
        try {
            username = UserAccount.normalizeUsername(request.username());
        } catch (IllegalArgumentException exception) {
            encoder.matches(request.password(), dummyHash);
            throw badCredentials();
        }
        var account = accounts.findByUsername(username).orElse(null);
        String hash = account == null ? dummyHash : account.getPasswordHash();
        boolean matches;
        try {
            matches = encoder.matches(request.password(), hash);
        } catch (IllegalArgumentException exception) {
            // Unusable/legacy hashes must not leak account state to the client.
            throw badCredentials();
        }
        if (!matches || account == null || !account.isEnabled() || !account.getEmployee().isActive()) {
            throw badCredentials();
        }
        return tokens.issue(account.getId());
    }

    private BadCredentialsException badCredentials() {
        return new BadCredentialsException("Invalid username or password");
    }
}
