package com.richcomes.garage.auth.config;

import com.richcomes.garage.auth.security.AccountJwtAuthenticationConverter;
import com.richcomes.garage.auth.security.SecurityErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
            AccountJwtAuthenticationConverter converter, SecurityErrorHandlers errors) throws Exception {
        return http
                // Bearer-header-only API: no cookie authentication, Basic login or server sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/employees", "/api/employees/{id}")
                            .hasAuthority("employee:read")
                        .requestMatchers(HttpMethod.POST, "/api/employees").hasAuthority("employee:create")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/{id}").hasAuthority("employee:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/employees/{id}/deactivate")
                            .hasAuthority("employee:deactivate")
                        .anyRequest().denyAll())
                .build();
    }
}
