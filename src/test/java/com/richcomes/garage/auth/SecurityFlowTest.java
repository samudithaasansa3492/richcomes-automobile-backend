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
    @Autowired private com.richcomes.garage.auth.recovery.RecoveryService recovery;
    @Autowired private com.richcomes.garage.auth.service.CustomerAccountService customerAccountService;
    @Autowired private com.richcomes.garage.auth.service.CustomerRegistrationDelegationService customerDelegations;
    @Autowired private com.richcomes.garage.auth.service.CashierPermissionService cashierPermissions;
    @Autowired private com.richcomes.garage.customer.repository.CustomerRepository customerRepository;
    private MockMvc mvc;
    private UserAccount account;
    private Employee employee;
    private Role role;
    private String token;
    private static final String PASSWORD = "correct horse battery staple";
    private static final String LOGIN_JSON = "{\"username\":\"cashier.one\",\"password\":\"" + PASSWORD + "\"}";

    @BeforeEach
    void setUp() {
        reset(accounts, accountService, recovery, customerAccountService, customerDelegations, customerRepository, cashierPermissions);
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
    void recoveredCredentialsRejectOldTokensAndAcceptNewVersion() throws Exception {
        account.resetPasswordHash(passwords.encode("replacement synthetic password"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String fresh = tokens.issue(42L, account.getCredentialVersion()).accessToken();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void independentAdminCanRequestRecoveryButCannotReadBusinessData() throws Exception {
        var admin = new Role("ADMIN", "Technical support");
        admin.grantPermission(new Permission("account:read", "Accidental grant"));
        var developer = UserAccount.technical("developer.one", passwords.encode(PASSWORD), admin);
        developer.enable();
        ReflectionTestUtils.setField(developer, "id", 42L);
        when(accounts.findOneById(42L)).thenReturn(Optional.of(developer));
        mvc.perform(post("/api/technical/recovery/request").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"client.owner\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(recovery).request("client.owner", 42L);
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").isEmpty());
    }

    @Test
    void businessRoleCannotAcquireTechnicalAuthorityThroughPermissionGrant() throws Exception {
        role.grantPermission(new Permission("technical:recovery", "Accidental grant"));
        mvc.perform(post("/api/technical/recovery/request").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"client.owner\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recovery);
    }

    @Test
    void recoveryRequestIsGenericAndEnrollmentRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/recovery/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"absent.user\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        doThrow(new com.richcomes.garage.auth.recovery.RecoveryFailure()).when(recovery).request("absent.user", null);
        mvc.perform(post("/api/auth/recovery/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"absent.user\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        mvc.perform(post("/api/auth/recovery/email").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void technicianCannotLoginOrUseAnExistingToken() throws Exception {
        employee.setPosition(com.richcomes.garage.employee.entity.EmployeePosition.TECHNICIAN);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void developerCannotInheritBusinessAccessEvenWithOwnerAndBusinessGrants() throws Exception {
        var admin = new Role("ADMIN", "Developer");
        admin.grantPermission(new Permission("account:manage", "Manage accounts"));
        account.assignRole(admin);
        account.assignRole(new Role("OWNER", "Owner"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isEmpty());
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/accounts/43/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"OWNER\"]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    @Test
    void legacyTechnicianRoleCannotAuthenticateEvenOnCashierEmployee() throws Exception {
        account.assignRole(new Role("TECHNICIAN", "Legacy"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
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
    void accidentalCashierEmployeeAndAccountGrantsRemainDeniedEvenWithOldJwt() throws Exception {
        // Old migrations do not grant these to CASHIER, but a later accidental
        // role-wide grant must NEVER create a staff-administrator bypass.
        role.grantPermission(new Permission("employee:create", "Unsafe staff grant"));
        role.grantPermission(new Permission("employee:update", "Unsafe staff grant"));
        role.grantPermission(new Permission("employee:deactivate", "Unsafe staff grant"));
        role.grantPermission(new Permission("account:read", "Unsafe account grant"));
        role.grantPermission(new Permission("account:create", "Unsafe account grant"));
        role.grantPermission(new Permission("account:manage", "Unsafe account grant"));
        // A malformed direct grant must not bypass the OWNER-only boundary either.
        account.grantDirectPermission(new Permission("account:manage", "Unapproved direct grant"));
        for (var request : java.util.List.of(
                get("/api/employees"), post("/api/employees"),
                put("/api/employees/5"), patch("/api/employees/5/deactivate"),
                get("/api/accounts"), post("/api/accounts"),
                put("/api/accounts/5/roles"), patch("/api/accounts/5/disable"),
                patch("/api/accounts/5/enable"))) {
            mvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
        verifyNoInteractions(accountService);
    }

    @Test
    void employeeReadRequiresReadPermission() throws Exception {
        // The OWNER owns administrative privileges; CASHIER never inherits them.
        account.assignRole(new Role("OWNER", "Owner"));
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        role.revokePermission(new Permission("employee:read", "Read"));
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void eachEmployeeMutationRequiresItsSpecificPermission() throws Exception {
        // The OWNER owns administrative privileges; CASHIER never inherits them.
        account.assignRole(new Role("OWNER", "Owner"));
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
        account.assignRole(new Role("OWNER", "Owner"));
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
                .claim("roles", java.util.List.of("OWNER")).build();
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
        // The OWNER owns administrative privileges; CASHIER never inherits them.
        account.assignRole(new Role("OWNER", "Owner"));
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
        // The OWNER owns administrative privileges; CASHIER never inherits them.
        account.assignRole(new Role("OWNER", "Owner"));
        role.grantPermission(new Permission("account:manage", "Manage accounts"));
        when(accountService.disableAccount(7L, 42L)).thenThrow(new com.richcomes.garage.auth.exception.AccountOperationException(
                com.richcomes.garage.auth.exception.AccountOperationException.Code.LAST_ACTIVE_OWNER,
                "At least one active administrator must remain"));
        mvc.perform(patch("/api/accounts/7/disable").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actorId\":999}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ACTIVE_OWNER"));
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
        // The OWNER owns administrative privileges; CASHIER never inherits them.
        account.assignRole(new Role("OWNER", "Owner"));
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
        // The OWNER owns administrative privileges; CASHIER never inherits them.
        account.assignRole(new Role("OWNER", "Owner"));
        role.grantPermission(new Permission("account:create", "Create account"));
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
        verifyNoInteractions(accountService);
    }

    @Test
    void unconfiguredBusinessCategoryRouteIsDeniedEvenToAuthenticatedStaff() throws Exception {
        // Category module routes require a separately approved permission mapping.
        // Do not make them public or bypass JWT authentication to fix an obsolete 200 test.
        mvc.perform(get("/api/service-categories")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerAndDelegationRoutesDenyAnonymousOrUndelegatedCashier() throws Exception {
        mvc.perform(post("/api/customer-accounts")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customer/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customer-accounts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/customer-accounts/5/activate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/accounts/8/permissions/customer-registration")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/customer/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanLogInAndReadOnlyOwnProfileWithExistingJwt() throws Exception {
        var customer = new com.richcomes.garage.customer.entity.Customer();
        ReflectionTestUtils.setField(customer, "id", 9L);
        customer.setStatus(com.richcomes.garage.customer.enums.CustomerStatus.ACTIVE);
        customer.setName("Test Customer");
        customer.setMobile("0770000000");
        customer.setCity("Test City");
        customer.setEmail("customer@example.invalid");
        var customerIdentity = UserAccount.customer(customer, "customer.one", passwords.encode(PASSWORD),
                new Role("CUSTOMER", "Customer identity"));
        customerIdentity.enable();
        ReflectionTestUtils.setField(customerIdentity, "id", 42L);
        when(accounts.findOneById(42L)).thenReturn(Optional.of(customerIdentity));
        when(accounts.findByUsername("customer.one")).thenReturn(Optional.of(customerIdentity));
        when(customerRepository.findById(9L)).thenReturn(Optional.of(customer));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"customer.one\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(9))
                .andExpect(jsonPath("$.employeeId").isEmpty())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        mvc.perform(get("/api/customer/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(9))
                .andExpect(jsonPath("$.name").value("Test Customer"))
                .andExpect(jsonPath("$.nic").doesNotExist())
                .andExpect(jsonPath("$.creditLimit").doesNotExist());
        for (var forbidden : java.util.List.of("/api/v1/customers/10", "/api/accounts", "/api/employees")) {
            mvc.perform(get(forbidden).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        verify(customerRepository).findById(9L);

        customer.setStatus(com.richcomes.garage.customer.enums.CustomerStatus.SUSPENDED);
        mvc.perform(get("/api/customer/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delegatedCashierMayEnrollUntilOwnerRevokesPermissionDespiteOldJwt() throws Exception {
        var registration = new Permission("customer:register", "Register customer");
        mvc.perform(post("/api/customer-accounts").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        account.grantDirectPermission(registration);
        when(customerAccountService.register(any(), any())).thenReturn(
                new com.richcomes.garage.auth.dto.CustomerAccountResponse(51L, 7L, "customer.new", false));
        var validBody = "{\"customerId\":7,\"username\":\"customer.new\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post("/api/customer-accounts").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(header().string("Location", "/api/customer-accounts/51"));
        verify(customerAccountService).register(any(), any());
        account.revokeDirectPermission(registration);
        mvc.perform(post("/api/customer-accounts").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(customerAccountService);
    }

    @Test
    void ownerDelegationEndpointRequiresOwnerAuthorityAndUsesAuthenticatedActor() throws Exception {
        role.grantPermission(new Permission("customer:delegate_register", "Delegate cashier"));
        mvc.perform(put("/api/accounts/7/permissions/customer-registration")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        account.assignRole(new Role("OWNER", "Garage owner"));
        when(customerDelegations.setAllowed(eq(7L), eq(true), any()))
                .thenReturn(new com.richcomes.garage.auth.dto.CustomerRegistrationDelegationResponse(7L, true));
        mvc.perform(put("/api/accounts/7/permissions/customer-registration")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"actorId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationPermitted").value(true));
        verify(customerDelegations).setAllowed(eq(7L), eq(true),
                argThat(actor -> actor.accountId().equals(42L) && actor.roles().contains("OWNER")));
    }

    @Test
    void cashierGetsOnlyIndividuallyDelegatedVehiclePermissionsAndRevocationIsImmediate() throws Exception {
        var read = new Permission("vehicle:read", "Read vehicles");
        var update = new Permission("vehicle:update", "Update vehicles");
        // An accidental CASHIER-wide role grant cannot grant all cashiers access.
        role.grantPermission(read);
        mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        account.grantDirectPermission(read);
        mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(put("/api/v1/vehicles/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        account.grantDirectPermission(update);
        mvc.perform(put("/api/v1/vehicles/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        account.revokeDirectPermission(read);
        mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/vehicles/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void auditAndPermissionDelegationCannotBeObtainedThroughCashierRole() throws Exception {
        role.grantPermission(new Permission("permission:delegate", "Unsafe global grant"));
        role.grantPermission(new Permission("audit:read", "Unsafe global grant"));
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts/7/permissions")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cashierPermissions);
    }

    @Test
    void onlyOwnerCanCallAuditedPerCashierGrantAndReadEndpoints() throws Exception {
        var ownerRole = new Role("OWNER", "Garage owner");
        ownerRole.grantPermission(new Permission("permission:delegate", "Delegate business actions"));
        ownerRole.grantPermission(new Permission("vehicle:read", "Read vehicles"));
        account.assignRole(ownerRole);
        when(cashierPermissions.change(eq(7L), any(), any()))
                .thenReturn(new com.richcomes.garage.auth.dto.CashierPermissionsResponse(
                        7L, java.util.Set.of("vehicle:read")));
        var body = "{\"permissionCode\":\"vehicle:read\",\"enabled\":true,\"actorId\":999}";
        mvc.perform(put("/api/accounts/7/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedPermissions[0]").value("vehicle:read"));
        verify(cashierPermissions).change(eq(7L),
                argThat(request -> request.permissionCode().equals("vehicle:read") && request.enabled()),
                argThat(actor -> actor.accountId().equals(42L) && actor.roles().contains("OWNER")));
    }

    @RestController
    static class BusinessProbe {
        @GetMapping("/api/v1/vehicles") Map<String, Boolean> vehicles() { return Map.of("ok", true); }
        @PutMapping("/api/v1/vehicles/{id}") Map<String, Boolean> updateVehicle() { return Map.of("ok", true); }
        @GetMapping("/api/v1/audit-logs") Map<String, Boolean> audit() { return Map.of("ok", true); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, SecurityErrorHandlers.class, AccountJwtAuthenticationConverter.class,
            AuthController.class, AuthExceptionHandler.class, AuthenticationService.class, AccessTokenService.class,
            EmployeeProbe.class, com.richcomes.garage.auth.controller.AccountController.class,
            com.richcomes.garage.auth.controller.AccountExceptionHandler.class,
            com.richcomes.garage.auth.controller.CustomerAccountController.class,
            com.richcomes.garage.auth.controller.CustomerRegistrationDelegationController.class,
            com.richcomes.garage.auth.controller.CustomerMeController.class,
            com.richcomes.garage.auth.controller.CashierPermissionController.class,
            BusinessProbe.class})
    static class TestConfig {
        @Bean com.richcomes.garage.auth.recovery.RecoveryService recoveryService() {
            return mock(com.richcomes.garage.auth.recovery.RecoveryService.class);
        }
        @Bean com.richcomes.garage.auth.recovery.RecoveryController recoveryController(
                com.richcomes.garage.auth.recovery.RecoveryService service) {
            return new com.richcomes.garage.auth.recovery.RecoveryController(service);
        }
        @Bean com.richcomes.garage.auth.recovery.RecoveryExceptionHandler recoveryExceptionHandler() {
            return new com.richcomes.garage.auth.recovery.RecoveryExceptionHandler();
        }
        @Bean com.richcomes.garage.auth.service.UserAccountService accountService() {
            return mock(com.richcomes.garage.auth.service.UserAccountService.class);
        }
        @Bean UserAccountRepository accounts() { return mock(UserAccountRepository.class); }
        @Bean com.richcomes.garage.customer.repository.CustomerRepository customerRepository() {
            return mock(com.richcomes.garage.customer.repository.CustomerRepository.class);
        }
        @Bean com.richcomes.garage.auth.service.CustomerAccountService customerAccountService() {
            return mock(com.richcomes.garage.auth.service.CustomerAccountService.class);
        }
        @Bean com.richcomes.garage.auth.service.CustomerRegistrationDelegationService customerDelegations() {
            return mock(com.richcomes.garage.auth.service.CustomerRegistrationDelegationService.class);
        }
        @Bean com.richcomes.garage.auth.service.CashierPermissionService cashierPermissions() {
            return mock(com.richcomes.garage.auth.service.CashierPermissionService.class);
        }
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
