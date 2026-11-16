package com.richcomes.garage.auth;

import com.richcomes.garage.auth.dto.CashierPermissionChangeRequest;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.CashierPermissionService;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL persistence + transaction boundary. Opt in ONLY via
 * validate-spring-context-isolated.sh's localhost-only, disposable container.
 * Never run this test using production or shared Supabase configuration.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RICHCOMES_LOCAL_PG_TESTS", matches = "YES")
class CashierPermissionPostgresIT {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EmployeeRepository employees;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired CashierPermissionService grants;
    @Autowired UserAccountService staffAccounts;

    @Test
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

        assertEquals(Set.of("vehicle:read"), grants.change(target, allow, owner).grantedPermissions());
        assertEquals(1, auditCount(target));
        assertEquals(Set.of("vehicle:read"), currentDirectCodes(target));

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

        assertTrue(grants.change(target, deny, owner).grantedPermissions().isEmpty());
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(2, auditCount(target));
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
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(4, auditCount(target));
        staffAccounts.enableAccount(target);
        assertTrue(currentDirectCodes(target).isEmpty());
        assertEquals(4, auditCount(target));
    }

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
