package com.richcomes.garage.auth;

import com.richcomes.garage.auth.dto.CashierPermissionChangeRequest;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.PermissionRepository;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.CashierPermissionService;
import com.richcomes.garage.auth.service.AccessTokenService;
import com.richcomes.garage.auth.service.UserAccountService;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.entity.EmployeePosition;
import com.richcomes.garage.employee.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.http.MediaType;
import com.jayway.jsonpath.JsonPath;
import com.richcomes.garage.auth.dto.AccountRolesUpdateRequest;
import com.richcomes.garage.employee.service.EmployeeService;
import java.util.concurrent.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL persistence + transaction boundary. Opt in ONLY via
 * validate-spring-context-isolated.sh's localhost-only, disposable container.
 * Never run this test using production or shared Supabase configuration.
 */
@SpringBootTest
@ActiveProfiles("isolated-pg")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "RICHCOMES_LOCAL_PG_TESTS", matches = "YES")
class CashierPermissionPostgresIT {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EmployeeRepository employees;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired CashierPermissionService grants;
    @Autowired UserAccountService staffAccounts;
    @Autowired AccessTokenService tokenService;
    @Autowired MockMvc mvc;
    @Autowired EmployeeService employeeService;

    @Test
    @Order(3)
    void committedGrantAndAuditRollbackAndRevokeAreAtomicInRealPostgres() throws Exception {
        assertIsolatedPostgres();
        var tx = new TransactionTemplate(transactions);
        var fixture = tx.execute(status -> {
            var ownerRole = roles.findByCode("OWNER").orElseThrow();
            var cashierRole = roles.findByCode("CASHIER").orElseThrow();
            var ownerEmployee = employees.saveAndFlush(staff("OWNER", EmployeePosition.OWNER));
            var cashierEmployee = employees.saveAndFlush(staff("CASHIER", EmployeePosition.CASHIER));

            var ownerAccount = new UserAccount(ownerEmployee, uniqueUsername("owner"), "synthetic-test-hash");
            ownerAccount.assignRole(ownerRole);
            ownerAccount.enable();
            accounts.saveAndFlush(ownerAccount);

            var cashierAccount = new UserAccount(cashierEmployee, uniqueUsername("cashier"), "synthetic-test-hash");
            cashierAccount.assignRole(cashierRole);
            cashierAccount.enable();
            accounts.saveAndFlush(cashierAccount);
            return new Fixture(ownerAccount.getId(), cashierAccount.getId(),
                    AccountPrincipal.from(ownerAccount));
        });
        assertNotNull(fixture);

        var allow = new CashierPermissionChangeRequest("vehicle:read", true);
        var deny = new CashierPermissionChangeRequest("vehicle:read", false);
        Long target = fixture.cashierId();
        var owner = fixture.owner();
        // Signed JWT is issued once; only DB grants change between requests.
        String cashierBearer = "Bearer " + tokenService.issue(target).accessToken();
        mvc.perform(get("/api/v1/vehicles").header("Authorization", cashierBearer))
                .andExpect(status().isForbidden());

        assertEquals(Set.of("vehicle:read"), grants.change(target, allow, owner).grantedPermissions());
        assertEquals(1, auditCount(target));
        assertEquals(Set.of("vehicle:read"), currentDirectCodes(target));
        mvc.perform(get("/api/v1/vehicles").header("Authorization", cashierBearer))
                .andExpect(status().isOk());

        // Replaying the exact same grant must not double-write the audit.
        assertEquals(Set.of("vehicle:read"), grants.change(target, allow, owner).grantedPermissions());
        assertEquals(1, auditCount(target));

        // The DB must rollback BOTH the account_permissions DELETE and its
        // audit entry when the enclosing transaction is explicitly rolled back.
        tx.executeWithoutResult(status -> {
            assertTrue(grants.change(target, deny, owner).grantedPermissions().isEmpty());
            status.setRollbackOnly();
        });
        assertEquals(Set.of("vehicle:read"), currentDirectCodes(target));
        assertEquals(1, auditCount(target));
        mvc.perform(get("/api/v1/vehicles").header("Authorization", cashierBearer))
                .andExpect(status().isOk());

        assertTrue(grants.change(target, deny, owner).grantedPermissions().isEmpty());
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(2, auditCount(target));
        mvc.perform(get("/api/v1/vehicles").header("Authorization", cashierBearer))
                .andExpect(status().isForbidden());
        var actions = sql.queryForList("""
                SELECT granted FROM garage.account_permission_audit
                 WHERE target_account_id = ? AND permission_code = 'vehicle:read'
                 ORDER BY occurred_at ASC, id ASC
                """, Boolean.class, target);
        assertEquals(java.util.List.of(true, false), actions);

        // Re-grant, then actually disable/re-enable that staff account using
        // the production service. Its grants must be durably revoked and
        // audited, and re-enable must not resurrect them.
        grants.change(target, allow, owner);
        assertEquals(3, auditCount(target));
        staffAccounts.disableAccount(target, fixture.ownerId());
        mvc.perform(get("/api/v1/vehicles").header("Authorization", cashierBearer))
                .andExpect(status().isUnauthorized());
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(4, auditCount(target));
        staffAccounts.enableAccount(target);
        mvc.perform(get("/api/v1/vehicles").header("Authorization", cashierBearer))
                .andExpect(status().isForbidden());
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(4, auditCount(target));

        // Simulate a legacy database row predating this conservative policy:
        // even with an old signed JWT, financial reports must remain forbidden.
        var denied = assertThrows(AccountOperationException.class,
                () -> grants.change(target,
                        new CashierPermissionChangeRequest("report:read", true), owner));
        assertEquals(AccountOperationException.Code.INVALID_REQUEST, denied.getCode());
        tx.executeWithoutResult(status -> {
            var saved = accounts.findOneById(target).orElseThrow();
            saved.grantDirectPermission(permissions.findByCode("report:read").orElseThrow());
            accounts.saveAndFlush(saved);
        });
        assertEquals(Set.of("report:read"), currentDirectCodes(target));
        mvc.perform(get("/api/v1/reports/financial")
                .header("Authorization", cashierBearer)).andExpect(status().isForbidden());

        // Cleanup must persist and audit one revoke without any new grant path.
        assertTrue(grants.change(target,
                new CashierPermissionChangeRequest("report:read", false), owner)
                .grantedPermissions().isEmpty());
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(5, auditCount(target));
        assertEquals(Boolean.FALSE, sql.queryForObject("""
                SELECT granted FROM garage.account_permission_audit
                 WHERE target_account_id = ? AND permission_code = 'report:read'
                """, Boolean.class, target));
    }


    @Test
    @Order(1)
    void accountLifecycleAndSelfProtectionOverSignedJwtHttp() throws Exception {
        assertIsolatedPostgres();
        assertEquals(0L, accounts.countActiveOwners(), "Expected fresh isolated fixture");
        var owner = createOwnerFixture();
        String ownerBearer = "Bearer " + tokenService.issue(owner.id()).accessToken();
        var employee = employees.saveAndFlush(staff("CASHIER", EmployeePosition.CASHIER));
        String username = uniqueUsername("lifecycle");
        String password = UUID.randomUUID().toString();
        String login = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        Long target = null;
        try {
            mvc.perform(post("/api/accounts").header("Authorization", ownerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                            {"employeeId":"%s","username":"%s","password":"%s","roleCodes":["CASHIER"]}
                            """.formatted(employee.getId(), username, password)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
            target = accounts.findByUsername(username).orElseThrow().getId();
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/accounts/{id}", target).header("Authorization", ownerBearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));
            mvc.perform(get("/api/accounts").param("page", "0").param("size", "1")
                            .header("Authorization", ownerBearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
            mvc.perform(get("/api/accounts").param("page", "1").param("size", "1")
                            .header("Authorization", ownerBearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(1));
            mvc.perform(get("/api/accounts").param("size", "0").header("Authorization", ownerBearer))
                    .andExpect(status().isBadRequest());
            mvc.perform(patch("/api/accounts/{id}/enable", target).header("Authorization", ownerBearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
            String response = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(login))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                    .andReturn().getResponse().getContentAsString();
            String bearer = "Bearer " + JsonPath.<String>read(response, "$.accessToken");
            mvc.perform(get("/api/accounts").header("Authorization", bearer))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/accounts/{id}/roles", target).header("Authorization", ownerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"OWNER\"]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.roleCodes[0]").value("OWNER"));
            mvc.perform(get("/api/accounts").header("Authorization", bearer)).andExpect(status().isOk());
            mvc.perform(put("/api/accounts/{id}/roles", target).header("Authorization", ownerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"CASHIER\"]}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/accounts").header("Authorization", bearer)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/accounts/{id}/disable", owner.id()).header("Authorization", ownerBearer))
                    .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION"));
            mvc.perform(put("/api/accounts/{id}/roles", owner.id()).header("Authorization", ownerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"CASHIER\"]}"))
                    .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION"));
            mvc.perform(patch("/api/employees/{id}/deactivate", owner.employeeId())
                            .header("Authorization", ownerBearer))
                    .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION"));
            mvc.perform(patch("/api/accounts/{id}/disable", target).header("Authorization", ownerBearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
            mvc.perform(get("/api/auth/me").header("Authorization", bearer))
                    .andExpect(status().isUnauthorized());
            assertEquals(1L, accounts.countActiveOwners());
            assertFalse(accounts.findById(target).orElseThrow().isEnabled());
        } finally {
            // Only this test's synthetic rows in the guarded, throwaway database.
            if (target != null) sql.update("UPDATE garage.user_accounts SET enabled=false WHERE id=?", target);
            sql.update("UPDATE garage.user_accounts SET enabled=false WHERE id=?", owner.id());
        }
    }

    @Test
    @Order(2)
    void competingOwnerRemovalPathsPreserveExactlyOneActiveOwnerInPostgres() throws Exception {
        assertIsolatedPostgres();
        for (Removal[] pair : java.util.List.of(
                new Removal[]{Removal.DISABLE, Removal.ROLE},
                new Removal[]{Removal.DISABLE, Removal.EMPLOYEE},
                new Removal[]{Removal.ROLE, Removal.EMPLOYEE},
                new Removal[]{Removal.DISABLE, Removal.DISABLE},
                new Removal[]{Removal.ROLE, Removal.ROLE},
                new Removal[]{Removal.EMPLOYEE, Removal.EMPLOYEE})) {
            assertEquals(0L, accounts.countActiveOwners(), "Unexpected active owner outside test fixture");
            var first = createOwnerFixture();
            var second = createOwnerFixture();
            assertEquals(2L, accounts.countActiveOwners());
            var executor = Executors.newFixedThreadPool(2);
            var start = new CyclicBarrier(2);
            try {
                var a = executor.submit(() -> removeOwner(start, first, second, pair[0]));
                var b = executor.submit(() -> removeOwner(start, second, first, pair[1]));
                var results = java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
                assertEquals(1L, results.stream().filter("REMOVED"::equals).count(),
                        "Exactly one operation must succeed: " + java.util.Arrays.toString(pair));
                assertEquals(1L, results.stream().filter("LAST_ACTIVE_OWNER"::equals).count(),
                        "Other operation must be rejected by last-owner guard");
                assertEquals(1L, accounts.countActiveOwners());
                // Independent SQL check for this fixture (no technical or mixed roles).
                assertEquals(1, sql.queryForObject("""
                        SELECT count(*) FROM garage.user_accounts a
                        JOIN garage.employees e ON e.id=a.employee_id
                        JOIN garage.user_account_roles ar ON ar.user_account_id=a.id
                        JOIN garage.roles r ON r.id=ar.role_id
                        WHERE a.id IN (?,?) AND a.enabled AND e.active AND r.code='OWNER'
                        """, Integer.class, first.id(), second.id()));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
                sql.update("UPDATE garage.user_accounts SET enabled=false WHERE id IN (?,?)",
                        first.id(), second.id());
            }
        }
    }

    private OwnerFixture createOwnerFixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            var employee = employees.saveAndFlush(staff("OWNER", EmployeePosition.OWNER));
            var account = new UserAccount(employee, uniqueUsername("owner"), "synthetic-test-hash");
            account.assignRole(roles.findByCode("OWNER").orElseThrow());
            account.enable();
            return new OwnerFixture(accounts.saveAndFlush(account).getId(), employee.getId());
        });
    }

    private String removeOwner(CyclicBarrier start, OwnerFixture target, OwnerFixture actor, Removal action)
            throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            // Production transactional services, not a mocked guard or direct SQL mutation.
            switch (action) {
                case DISABLE -> staffAccounts.disableAccount(target.id(), actor.id());
                case ROLE -> staffAccounts.replaceRoles(target.id(),
                        new AccountRolesUpdateRequest(Set.of("CASHIER")), actor.id());
                case EMPLOYEE -> employeeService.deactivateEmployee(target.employeeId(), actor.id());
            }
            return "REMOVED";
        } catch (AccountOperationException failure) {
            return failure.getCode().name();
        }
    }

    private enum Removal { DISABLE, ROLE, EMPLOYEE }
    private record OwnerFixture(Long id, UUID employeeId) {}

    private void assertIsolatedPostgres() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            var url = connection.getMetaData().getURL();
            assertTrue(url.matches(
                    "^jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/richcomes_context(?:\\?.*)?$"),
                    "Refusing permission-write integration test outside localhost disposable richcomes_context");
        }
    }

    private int auditCount(Long target) {
        return sql.queryForObject(
                "SELECT COUNT(*) FROM garage.account_permission_audit WHERE target_account_id = ?",
                Integer.class, target);
    }

    private Set<String> currentDirectCodes(Long id) {
        return accounts.findOneById(id).orElseThrow().getDirectPermissions().stream()
                .map(permission -> permission.getCode()).collect(Collectors.toSet());
    }

    private static Employee staff(String kind, EmployeePosition position) {
        var e = new Employee();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        e.setEmployeeCode("SEC-" + kind.substring(0, 1) + suffix);
        e.setNic("SEC" + suffix);
        e.setFirstName("Synthetic");
        e.setLastName("Security");
        e.setPhone("0700000000");
        e.setPosition(position);
        e.setPaymentRate(BigDecimal.ZERO);
        return e;
    }

    private static String uniqueUsername(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    private record Fixture(Long ownerId, Long cashierId, AccountPrincipal owner) {}
}
