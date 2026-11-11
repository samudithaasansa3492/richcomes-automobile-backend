package com.richcomes.garage.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Explicitly accept only bcrypt; no plaintext or legacy encoder fallback.
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }
}
