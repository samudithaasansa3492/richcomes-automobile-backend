package com.richcomes.garage.auth;

import com.richcomes.garage.auth.config.JwtConfig;
import com.richcomes.garage.auth.config.JwtSettings;
import com.richcomes.garage.auth.config.SecurityConfig;
import com.richcomes.garage.auth.controller.AuthController;
import com.richcomes.garage.auth.controller.AuthExceptionHandler;
import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountJwtAuthenticationConverter;
import com.richcomes.garage.auth.security.SecurityErrorHandlers;
import com.richcomes.garage.auth.service.AccessTokenService;
import com.richcomes.garage.auth.service.AuthenticationService;
import com.richcomes.garage.employee.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.security.KeyPair;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filter chain, JWT signing/verification and auth controller; repositories are mocks. */
@SpringJUnitWebConfig(SecurityFlowTest.TestConfig.class)
class SecurityFlowTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserAccountRepository accounts;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AccessTokenService tokens;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtSettings settings;
    @Autowired private com.richcomes.garage.auth.service.UserAccountService accountService;
    private MockMvc mvc;
    private UserAccount account;
    private Employee employee;
    private Role role;
    private String token;
    private static final String PASSWORD = "correct horse battery staple";
    private static final String LOGIN_JSON = "{\"username\":\"cashier.one\",\"password\":\"" + PASSWORD + "\"}";

    @BeforeEach
    void setUp() {
        reset(accounts, accountService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        role = new Role("CASHIER", "Cashier");
        role.grantPermission(new Permission("employee:read", "Read employees"));
        account = new UserAccount(employee, "cashier.one", passwords.encode(PASSWORD));
        ReflectionTestUtils.setField(account, "id", 42L);
        account.assignRole(role);
        account.enable();
        when(accounts.findByUsername("cashier.one")).thenReturn(Optional.of(account));
        when(accounts.findOneById(42L)).thenReturn(Optional.of(account));
        token = tokens.issue(42L).accessToken();
    }

    @Test
    void loginIsPublicAndReturnsBearerTokenWithoutSession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void wrongPasswordReturnsGeneric401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cashier.one\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void malformedLoginRequestReturnsSafe400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid login request"));
    }

    @Test
    void missingAndMalformedBearerTokensReturn401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsSafeCurrentIdentity() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(42))
                .andExpect(jsonPath("$.username").value("cashier.one"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void employeeReadRequiresReadPermission() throws Exception {
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        role.revokePermission(new Permission("employee:read", "Read"));
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void eachEmployeeMutationRequiresItsSpecificPermission() throws Exception {
        mvc.perform(post("/api/employees").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        role.grantPermission(new Permission("employee:create", "Create"));
        mvc.perform(post("/api/employees").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(put("/api/employees/42").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        role.grantPermission(new Permission("employee:update", "Update"));
        mvc.perform(put("/api/employees/42").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(patch("/api/employees/42/deactivate").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        role.grantPermission(new Permission("employee:deactivate", "Deactivate"));
        mvc.perform(patch("/api/employees/42/deactivate").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void unmappedEndpointsAndMethodsAreDeniedEvenForOwnerRole() throws Exception {
        account.assignRole(new Role("OWNER_ADMIN", "Owner"));
        for (String path : new String[]{"/api/customers", "/health", "/swagger-ui/index.html"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/employees/42").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void tokenPermissionClaimsCannotOverrideDatabasePermissions() throws Exception {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(settings.issuer()).audience(java.util.List.of(settings.audience()))
                .subject("42").issuedAt(JwtTokenTest.NOW).notBefore(JwtTokenTest.NOW)
                .expiresAt(JwtTokenTest.NOW.plusSeconds(900)).id("forged-authorities")
                .claim("permissions", java.util.List.of("employee:create"))
                .claim("roles", java.util.List.of("OWNER_ADMIN")).build();
        String tokenWithClaims = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        mvc.perform(post("/api/employees").header("Authorization", "Bearer " + tokenWithClaims))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountInvalidatesExistingBearerAccess() throws Exception {
        account.disable();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveEmployeeInvalidatesExistingBearerAccess() throws Exception {
        employee.setActive(false);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountCannotUsePreviouslyIssuedToken() throws Exception {
        when(accounts.findOneById(42L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        var past = Clock.fixed(JwtTokenTest.NOW.minusSeconds(901), ZoneOffset.UTC);
        String expired = new AccessTokenService(encoder, settings, past).issue(42L).accessToken();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void queryParameterTokenIsNotAccepted() throws Exception {
        mvc.perform(get("/api/auth/me").param("access_token", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void basicAuthenticationIsNotAccepted() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountEndpointsDenyAnonymousAndUnprivilegedAccess() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        for (var request : java.util.List.of(get("/api/accounts"), get("/api/accounts/7"),
                post("/api/accounts"), put("/api/accounts/7/roles"),
                patch("/api/accounts/7/enable"), patch("/api/accounts/7/disable"))) {
            mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(accountService);
    }

    @Test
    void accountReadPermissionDoesNotGrantManagement() throws Exception {
        role.grantPermission(new Permission("account:read", "Read accounts"));
        when(accountService.listAccounts(0, 20)).thenReturn(new com.richcomes.garage.auth.dto.AccountPageResponse(
                java.util.List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(patch("/api/accounts/7/disable").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void managementUsesAuthenticatedActorAndSafeDomainErrors() throws Exception {
        role.grantPermission(new Permission("account:manage", "Manage accounts"));
        when(accountService.disableAccount(7L, 42L)).thenThrow(new com.richcomes.garage.auth.exception.AccountOperationException(
                com.richcomes.garage.auth.exception.AccountOperationException.Code.LAST_ACTIVE_ADMIN,
                "At least one active administrator must remain"));
        mvc.perform(patch("/api/accounts/7/disable").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actorId\":999}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
        verify(accountService).disableAccount(7L, 42L);
        mvc.perform(put("/api/accounts/7/roles").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"CASHIER\"]}"))
                .andExpect(status().isOk());
        verify(accountService).replaceRoles(eq(7L), any(), eq(42L));
        mvc.perform(patch("/api/accounts/7/enable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(accountService).enableAccount(7L);
        mvc.perform(delete("/api/accounts/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountCreationReturnsLocationWithoutPassword() throws Exception {
        role.grantPermission(new Permission("account:create", "Create account"));
        when(accountService.createAccount(any())).thenReturn(new com.richcomes.garage.auth.dto.AccountResponse(
                7L, employee.getId(), "new.account", false, java.util.Set.of("CASHIER"), null, null));
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"employeeId":"00000000-0000-0000-0000-000000000001",
                         "username":"new.account","password":"correct horse battery staple","roleCodes":["CASHIER"]}
                        """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/accounts/7"))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void malformedAccountRequestsDoNotExposeInput() throws Exception {
        role.grantPermission(new Permission("account:create", "Create account"));
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
        verifyNoInteractions(accountService);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, SecurityErrorHandlers.class, AccountJwtAuthenticationConverter.class,
            AuthController.class, AuthExceptionHandler.class, AuthenticationService.class, AccessTokenService.class,
            EmployeeProbe.class, com.richcomes.garage.auth.controller.AccountController.class,
            com.richcomes.garage.auth.controller.AccountExceptionHandler.class})
    static class TestConfig {
        @Bean com.richcomes.garage.auth.service.UserAccountService accountService() {
            return mock(com.richcomes.garage.auth.service.UserAccountService.class);
        }
        @Bean UserAccountRepository accounts() { return mock(UserAccountRepository.class); }
        @Bean PasswordEncoder passwordEncoder() {
            // Reduced test-only cost; production PasswordConfig remains cost 12.
            return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4)));
        }
        @Bean Clock authClock() { return Clock.fixed(JwtTokenTest.NOW, ZoneOffset.UTC); }
        @Bean JwtSettings jwtSettings() { return new JwtSettings("https://test.invalid", "richcomes-api", null, null); }
        @Bean KeyPair jwtSigningKeys() throws Exception { return JwtTokenTest.newKeys(); }
        @Bean JwtEncoder jwtEncoder(KeyPair keys) { return new JwtConfig().jwtEncoder(keys); }
        @Bean JwtDecoder jwtDecoder(KeyPair keys, JwtSettings settings, Clock clock) {
            return new JwtConfig().jwtDecoder(keys, settings, clock);
        }
    }

    /** Probes test route authorization only; employee CRUD itself is outside these tests. */
    @RestController
    static class EmployeeProbe {
        @GetMapping({"/api/employees", "/api/employees/{id}"}) Map<String, Boolean> read() { return Map.of("ok", true); }
        @PostMapping("/api/employees") Map<String, Boolean> create() { return Map.of("ok", true); }
        @PutMapping("/api/employees/{id}") Map<String, Boolean> update() { return Map.of("ok", true); }
        @PatchMapping("/api/employees/{id}/deactivate") Map<String, Boolean> deactivate() { return Map.of("ok", true); }
    }
}
