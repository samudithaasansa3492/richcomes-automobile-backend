package com.richcomes.garage.auth.security;

import com.richcomes.garage.auth.repository.UserAccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserAccountRepository accounts;

    public AccountJwtAuthenticationConverter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long accountId;
        try {
            accountId = Long.parseLong(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw invalidToken();
        }
        var account = accounts.findOneById(accountId).orElseThrow(this::invalidToken);
        if (!AccountAccessPolicy.canAuthenticate(account)) {
            throw invalidToken();
        }
        var principal = AccountPrincipal.from(account);
        // Token role claims are deliberately ignored; use current DB permissions each request.
        var authorities = principal.permissions().stream().map(SimpleGrantedAuthority::new).toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Invalid access token", null));
    }
}
