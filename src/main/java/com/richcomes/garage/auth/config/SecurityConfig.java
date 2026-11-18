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
                        // Only non-sensitive liveness/readiness signals may be public.
                        // Health status has no DB URLs, errors or component details.
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        // Only these static account-recovery assets are public.
                        // No broader static directory bypass or anonymous business API.
                        .requestMatchers(HttpMethod.GET, "/account-setup.html",
                                "/account-setup.css", "/account-setup.js").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/recovery/request",
                                "/api/auth/recovery/reset", "/api/auth/recovery/email/confirm",
                                "/api/auth/recovery/initial-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/recovery/email").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/technical/recovery/request")
                            .hasAuthority("technical:recovery")
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/customer/me").hasAuthority("customer:self")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers", "/api/customer-accounts")
                            .hasAuthority("customer:register")
                        // Customer enrollment is delegatable; activation is NOT.
                        // Require an OWNER principal in addition to the current
                        // server-side customer:register authority (never JWT claims).
                        .requestMatchers(HttpMethod.PATCH, "/api/customer-accounts/{id}/activate")
                            .access((authentication, context) -> {
                                var current = authentication.get();
                                boolean permitted = current != null && current.isAuthenticated()
                                        && current.getPrincipal()
                                            instanceof com.richcomes.garage.auth.security.AccountPrincipal principal
                                        && principal.roles().contains("OWNER")
                                        && principal.permissions().contains("customer:register");
                                return new org.springframework.security.authorization.AuthorizationDecision(permitted);
                            })
                        // Proof attestation and invitation are never CASHIER-delegatable.
                        .requestMatchers(HttpMethod.POST, "/api/customer-accounts/{id}/verify-and-invite")
                            .access((authentication, context) -> {
                                var current = authentication.get();
                                boolean permitted = current != null && current.isAuthenticated()
                                        && current.getPrincipal()
                                            instanceof com.richcomes.garage.auth.security.AccountPrincipal principal
                                        && principal.roles().contains("OWNER")
                                        && principal.permissions().contains("customer:register");
                                return new org.springframework.security.authorization.AuthorizationDecision(permitted);
                            })
                        .requestMatchers(HttpMethod.PUT, "/api/accounts/{id}/permissions/customer-registration")
                            .hasAuthority("customer:delegate_register")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers", "/api/v1/customers/**")
                            .hasAuthority("customer:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/**")
                            .hasAuthority("customer:manage")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/customers/**")
                            .hasAuthority("customer:manage")
                        .requestMatchers(HttpMethod.GET, "/api/employees", "/api/employees/{id}")
                            .hasAuthority("employee:read")
                        .requestMatchers(HttpMethod.POST, "/api/employees").hasAuthority("employee:create")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/{id}").hasAuthority("employee:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/employees/{id}/deactivate")
                            .hasAuthority("employee:deactivate")
                        .requestMatchers(HttpMethod.POST, "/api/accounts").hasAuthority("account:create")
                        .requestMatchers(HttpMethod.GET, "/api/accounts", "/api/accounts/{id}")
                            .hasAuthority("account:read")
                        .requestMatchers(HttpMethod.PUT, "/api/accounts/{id}/roles").hasAuthority("account:manage")
                        .requestMatchers(HttpMethod.PATCH, "/api/accounts/{id}/enable", "/api/accounts/{id}/disable")
                            .hasAuthority("account:manage")
                        // Grant/revoke one listed action for one CASHIER; no blanket role changes.
                        .requestMatchers(HttpMethod.GET, "/api/accounts/{id}/permissions")
                            .hasAuthority("permission:delegate")
                        .requestMatchers(HttpMethod.PUT, "/api/accounts/{id}/permissions")
                            .hasAuthority("permission:delegate")

                        // Garage business data: permission checks remain method-scoped
                        // and default-deny for unimplemented or future write endpoints.
                        .requestMatchers(HttpMethod.GET, "/api/v1/vehicles", "/api/v1/vehicles/**")
                            .hasAuthority("vehicle:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/vehicles").hasAuthority("vehicle:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/vehicles/{id}")
                            .hasAuthority("vehicle:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/vehicles/{id}/activate",
                                "/api/v1/vehicles/{id}/deactivate").hasAuthority("vehicle:update")

                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments", "/api/v1/appointments/**")
                            .hasAuthority("appointment:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments").hasAuthority("appointment:create")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/appointments/{id}/approve",
                                "/api/v1/appointments/{id}/reject",
                                "/api/v1/appointments/{id}/complete",
                                "/api/v1/appointments/{id}/not-completed")
                            .hasAuthority("appointment:manage")

                        .requestMatchers(HttpMethod.GET, "/api/inventory/suppliers", "/api/inventory/suppliers/**",
                                "/api/inventory/categories", "/api/inventory/categories/**",
                                "/api/inventory/items", "/api/inventory/items/**",
                                "/api/inventory/grns", "/api/inventory/grns/**",
                                "/api/inventory/stock-transactions", "/api/inventory/stock-transactions/**")
                            .hasAuthority("inventory:read")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/suppliers",
                                "/api/inventory/categories", "/api/inventory/items",
                                "/api/inventory/grns").hasAuthority("inventory:create")
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/suppliers/{id}",
                                "/api/inventory/categories/{id}", "/api/inventory/items/{id}")
                            .hasAuthority("inventory:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/inventory/suppliers/{id}/activate",
                                "/api/inventory/suppliers/{id}/deactivate",
                                "/api/inventory/categories/{id}/activate",
                                "/api/inventory/categories/{id}/deactivate",
                                "/api/inventory/items/{id}/activate",
                                "/api/inventory/items/{id}/deactivate").hasAuthority("inventory:update")

                        .requestMatchers(HttpMethod.GET, "/api/service-categories", "/api/service-categories/**",
                                "/api/service-items", "/api/service-items/**",
                                "/api/service-jobs", "/api/service-jobs/**",
                                "/api/service-job-items", "/api/service-job-items/**",
                                "/api/service-job-parts", "/api/service-job-parts/**")
                            .hasAuthority("service:read")
                        .requestMatchers(HttpMethod.POST, "/api/service-categories", "/api/service-items",
                                "/api/service-jobs", "/api/service-job-items", "/api/service-job-parts")
                            .hasAuthority("service:create")
                        .requestMatchers(HttpMethod.PUT, "/api/service-categories/{id}",
                                "/api/service-items/{id}", "/api/service-jobs/{id}")
                            .hasAuthority("service:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/service-jobs/{id}/status")
                            .hasAuthority("service:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/service-categories/{id}",
                                "/api/service-items/{id}", "/api/service-jobs/{id}",
                                "/api/service-job-items/{id}", "/api/service-job-parts/{id}")
                            .hasAuthority("service:delete")

                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/invoices/{id}")
                            .hasAuthority("billing:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/calculate")
                            .hasAuthority("billing:calculate")

                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/export/pdf",
                                "/api/v1/reports/operational/pdf", "/api/v1/reports/stock/pdf",
                                "/api/v1/reports/financial/pdf", "/api/v1/reports/performance/pdf")
                            .hasAuthority("report:export")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/dashboard",
                                "/api/v1/reports/operational", "/api/v1/reports/stock",
                                "/api/v1/reports/financial", "/api/v1/reports/performance",
                                "/api/v1/reports/comprehensive").hasAuthority("report:read")

                        .requestMatchers(HttpMethod.GET, "/api/v1/history", "/api/v1/history/**")
                            .hasAuthority("history:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/history").hasAuthority("history:create")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/history/{id}")
                            .hasAuthority("history:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/history/{id}")
                            .hasAuthority("history:delete")

                        .requestMatchers(HttpMethod.GET, "/api/v1/reminders", "/api/v1/reminders/**")
                            .hasAuthority("reminder:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reminders",
                                "/api/v1/reminders/generate-from-history").hasAuthority("reminder:create")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/reminders/{id}",
                                "/api/v1/reminders/{id}/send", "/api/v1/reminders/{id}/cancel")
                            .hasAuthority("reminder:manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reminders/{id}")
                            .hasAuthority("reminder:delete")

                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications", "/api/v1/notifications/**")
                            .hasAuthority("notification:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/notifications/send",
                                "/api/v1/notifications/{id}/retry").hasAuthority("notification:send")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notifications/{id}")
                            .hasAuthority("notification:delete")

                        // Audit read is OWNER-only and deliberately NOT delegatable.
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-logs", "/api/v1/audit-logs/**")
                            .hasAuthority("audit:read")
                        .anyRequest().denyAll())
                .build();
    }
}
