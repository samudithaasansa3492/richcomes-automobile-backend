package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.service.AdminAccessGuard;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.entity.EmployeePosition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** H2 lock regression. PostgreSQL concurrency must also be validated before rollout. */
@SpringJUnitConfig(AdminAccessConcurrencyTest.Config.class)
class AdminAccessConcurrencyTest {
    @Configuration(proxyBeanMethods = false)
    @Import({IdentityPersistenceTest.TestConfiguration.class, AdminAccessGuard.class})
    static class Config {}
    @Autowired PlatformTransactionManager manager;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired AdminAccessGuard guard;
    @PersistenceContext EntityManager em;

    @Test void concurrentAccountAndEmployeeRemovalLeaveOneActiveOwner() throws Exception {
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Long[] ids = tx.execute(status -> {
            var role = roles.saveAndFlush(new Role("OWNER_ADMIN", "Admin"));
            return new Long[]{owner("one", role), owner("two", role)};
        });
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> remove(tx, start, ids[0], false));
            var second = executor.submit(() -> remove(tx, start, ids[1], true));
            start.countDown();
            var outcomes = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("REMOVED"::equals).count());
            assertEquals(1, outcomes.stream().filter("LAST_ACTIVE_ADMIN"::equals).count());
            assertEquals(1L, tx.execute(status -> accounts.countActiveOwners()).longValue());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    private String remove(TransactionTemplate tx, CountDownLatch start, Long id, boolean employee) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            tx.executeWithoutResult(status -> {
                guard.lockAdministration();
                var account = accounts.findById(id).orElseThrow();
                guard.protectOwner(account);
                if (employee) account.getEmployee().setActive(false);
                else account.disable();
                accounts.flush();
            });
            return "REMOVED";
        } catch (AccountOperationException failure) {
            return failure.getCode().name();
        }
    }
    private Long owner(String suffix, Role role) {
        var employee = new Employee();
        employee.setEmployeeCode("EMP-" + suffix);
        employee.setNic("NIC-" + suffix);
        employee.setFirstName("Test"); employee.setLastName("Owner");
        employee.setPhone("0700000000"); employee.setPosition(EmployeePosition.OWNER_ADMIN);
        employee.setPaymentRate(BigDecimal.ZERO);
        em.persist(employee);
        var account = new UserAccount(employee, "owner." + suffix, "encoded-test-hash");
        account.assignRole(role); account.enable();
        return accounts.saveAndFlush(account).getId();
    }
}
