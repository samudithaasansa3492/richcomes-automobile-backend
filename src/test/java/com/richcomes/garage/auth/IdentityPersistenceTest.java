package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Permission;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.PermissionRepository;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.entity.EmployeePosition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JPA mappings and repositories against a private H2 database.
 * Does not load GarageApplication, application.properties, Flyway or any external DB URL.
 * PostgreSQL migration syntax and grants require a separate PostgreSQL validation.
 */
@SpringJUnitConfig(IdentityPersistenceTest.TestConfiguration.class)
@Transactional
class IdentityPersistenceTest {

    private static final String TEST_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

    @Autowired private UserAccountRepository accounts;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void loadsEmployeeRolesAndPermissionsForNormalizedUsername() {
        Permission read = permissions.saveAndFlush(new Permission("employee:read", "View employees"));
        Role cashier = new Role("CASHIER", "Cashier access");
        cashier.grantPermission(read);
        roles.saveAndFlush(cashier);
        UserAccount account = new UserAccount(employee(), "  Cashier.One  ", TEST_HASH);
        account.assignRole(cashier);
        account.assignRole(cashier);
        accounts.saveAndFlush(account);
        entityManager.clear();

        UserAccount loaded = accounts.findByUsername(UserAccount.normalizeUsername("CASHIER.ONE"))
                .orElseThrow();
        assertEquals("cashier.one", loaded.getUsername());
        assertFalse(loaded.isEnabled(), "New accounts must require explicit activation");
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
        assertTrue(accounts.existsByEmployee_Id(loaded.getEmployee().getId()));
        assertEquals(1, loaded.getRoles().size());
        Role loadedRole = loaded.getRoles().iterator().next();
        assertEquals("employee:read", loadedRole.getPermissions().iterator().next().getCode());
        var persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertTrue(persistence.isLoaded(loaded, "employee"));
        assertTrue(persistence.isLoaded(loaded, "roles"));
        assertTrue(persistence.isLoaded(loadedRole, "permissions"));
    }

    @Test
    void rejectsDuplicateUsernameAfterNormalization() {
        accounts.saveAndFlush(new UserAccount(employee(), "cashier.one", TEST_HASH));
        Employee otherEmployee = employee();
        assertThrows(DataIntegrityViolationException.class,
                () -> accounts.saveAndFlush(new UserAccount(otherEmployee, " CASHIER.ONE ", TEST_HASH)));
    }

    @Test
    void rejectsSecondAccountForSameEmployee() {
        Employee employee = employee();
        accounts.saveAndFlush(new UserAccount(employee, "cashier.one", TEST_HASH));
        assertThrows(DataIntegrityViolationException.class,
                () -> accounts.saveAndFlush(new UserAccount(employee, "cashier.two", TEST_HASH)));
    }

    @Test
    void rejectsDuplicatePermissionCode() {
        permissions.saveAndFlush(new Permission("employee:read", "Read"));
        assertThrows(DataIntegrityViolationException.class,
                () -> permissions.saveAndFlush(new Permission("employee:read", "Duplicate")));
    }

    @Test
    void rejectsDuplicateRoleCode() {
        roles.saveAndFlush(new Role("CASHIER", "Cashier"));
        assertThrows(DataIntegrityViolationException.class,
                () -> roles.saveAndFlush(new Role("CASHIER", "Duplicate")));
    }

    @Test
    void removingRoleDoesNotDeleteSharedRoleOrPermission() {
        Permission read = permissions.saveAndFlush(new Permission("employee:read", "Read"));
        Role role = new Role("CASHIER", "Cashier");
        role.grantPermission(read);
        roles.saveAndFlush(role);
        UserAccount account = new UserAccount(employee(), "cashier.one", TEST_HASH);
        account.assignRole(role);
        accounts.saveAndFlush(account);
        account.removeRole(role);
        accounts.flush();
        entityManager.clear();

        assertTrue(accounts.findByUsername("cashier.one").orElseThrow().getRoles().isEmpty());
        assertTrue(roles.findByCode("CASHIER").isPresent());
        assertTrue(permissions.findByCode("employee:read").isPresent());
    }

    @Test
    void deletingAccountDoesNotDeleteEmployeeOrSharedAccessDefinitions() {
        Employee employee = employee();
        Role role = roles.saveAndFlush(new Role("CASHIER", "Cashier"));
        UserAccount account = new UserAccount(employee, "cashier.one", TEST_HASH);
        account.assignRole(role);
        accounts.saveAndFlush(account);
        Long accountId = account.getId();
        UUID employeeId = employee.getId();
        accounts.delete(account);
        accounts.flush();
        entityManager.clear();

        assertFalse(accounts.existsById(accountId));
        assertNotNull(entityManager.find(Employee.class, employeeId));
        assertTrue(roles.findByCode("CASHIER").isPresent());
    }

    @Test
    void accountUpdatePreservesCreationTime() {
        UserAccount account = accounts.saveAndFlush(new UserAccount(employee(), "cashier.one", TEST_HASH));
        var createdAt = account.getCreatedAt();
        account.enable();
        accounts.flush();
        assertEquals(createdAt, account.getCreatedAt());
        assertFalse(account.getUpdatedAt().isBefore(createdAt));
        entityManager.clear();
        assertTrue(accounts.findByUsername("cashier.one").orElseThrow().isEnabled());
    }

    private Employee employee() {
        Employee employee = new Employee();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        employee.setEmployeeCode("EMP-" + suffix);
        employee.setNic(suffix);
        employee.setFirstName("Test");
        employee.setLastName("Employee");
        employee.setPhone("0700000000");
        employee.setPosition(EmployeePosition.CASHIER);
        employee.setPaymentRate(BigDecimal.ZERO);
        entityManager.persist(employee);
        entityManager.flush();
        return employee;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .addScript("classpath:auth/create-test-schema.sql")
                    .build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.richcomes.garage.auth.entity", "com.richcomes.garage.employee.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
