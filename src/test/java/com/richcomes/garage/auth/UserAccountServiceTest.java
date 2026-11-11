package com.richcomes.garage.auth;

import com.richcomes.garage.auth.config.PasswordConfig;
import com.richcomes.garage.auth.dto.AccountCreateRequest;
import com.richcomes.garage.auth.dto.AccountRolesUpdateRequest;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.service.UserAccountService;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.repository.EmployeeRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests only: no Spring application context or external database. */
class UserAccountServiceTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private UserAccountRepository accounts;
    private EmployeeRepository employees;
    private RoleRepository roles;
    private PasswordEncoder encoder;
    private UserAccountService service;
    private Employee employee;
    private Role cashier;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        employees = mock(EmployeeRepository.class);
        roles = mock(RoleRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new UserAccountService(accounts, employees, roles, encoder, VALIDATORS.getValidator());
        employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        cashier = new Role("CASHIER", "Cashier");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(roles.findByCode("CASHIER")).thenReturn(Optional.of(cashier));
        when(encoder.encode(RAW_PASSWORD)).thenReturn("{bcrypt}test-encoded-value");
        when(accounts.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });
    }

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @Test
    void createsDisabledAccountWithNormalizedUsernameAndEncodedPassword() {
        var response = service.createAccount(request("  Cashier.One  ", RAW_PASSWORD, Set.of("CASHIER")));
        var saved = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertEquals("cashier.one", response.username());
        assertEquals(EMPLOYEE_ID, response.employeeId());
        assertFalse(response.enabled());
        assertEquals(Set.of("CASHIER"), response.roleCodes());
        assertEquals("{bcrypt}test-encoded-value", saved.getValue().getPasswordHash());
        assertFalse(response.toString().contains(RAW_PASSWORD));
        assertFalse(response.toString().contains("test-encoded-value"));
        verify(encoder).encode(RAW_PASSWORD);
    }

    @Test
    void rejectsMissingEmployeeWithoutHashingOrSaving() {
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());
        assertCode(EMPLOYEE_NOT_FOUND, () -> service.createAccount(validRequest()));
        verifyNoInteractions(encoder);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateEmployeeAccount() {
        when(accounts.existsByEmployee_Id(EMPLOYEE_ID)).thenReturn(true);
        assertCode(EMPLOYEE_ALREADY_HAS_ACCOUNT, () -> service.createAccount(validRequest()));
        verifyNoInteractions(encoder);
    }

    @Test
    void checksDuplicateNormalizedUsername() {
        when(accounts.existsByUsername("cashier.one")).thenReturn(true);
        assertCode(USERNAME_ALREADY_EXISTS,
                () -> service.createAccount(request(" CASHIER.ONE ", RAW_PASSWORD, Set.of("CASHIER"))));
        verifyNoInteractions(encoder);
    }

    @Test
    void rejectsUnknownRolesBeforeSaving() {
        assertCode(UNKNOWN_ROLE,
                () -> service.createAccount(request("cashier.one", RAW_PASSWORD, Set.of("UNKNOWN"))));
        verifyNoInteractions(encoder);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidRequestsWithoutIncludingSecretsInErrors() {
        assertCode(INVALID_REQUEST, () -> service.createAccount(null));
        var error = assertThrows(AccountOperationException.class,
                () -> service.createAccount(request("cashier.one", "secret", Set.of("CASHIER"))));
        assertEquals(INVALID_REQUEST, error.getCode());
        assertFalse(error.getMessage().contains("secret"));
        assertCode(INVALID_REQUEST, () -> service.createAccount(request("bad name", RAW_PASSWORD, Set.of("CASHIER"))));
        assertCode(INVALID_REQUEST, () -> service.createAccount(request("cashier.one", RAW_PASSWORD, Set.of())));
        assertCode(INVALID_REQUEST, () -> service.createAccount(request("cashier.one", RAW_PASSWORD, Set.of("cashier"))));
        verifyNoInteractions(encoder);
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimitEvenWhenCharacterCountIsValid() {
        assertCode(INVALID_REQUEST,
                () -> service.createAccount(request("cashier.one", "ආ".repeat(25), Set.of("CASHIER"))));
        verifyNoInteractions(encoder);
    }

    @Test
    void translatesConcurrentDuplicateUsernameConstraintFailure() {
        var cause = new ConstraintViolationException("duplicate",
                new SQLException("duplicate", "23505"), "uq_user_accounts_username");
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint", cause));
        assertCode(USERNAME_ALREADY_EXISTS, () -> service.createAccount(validRequest()));
    }

    @Test
    void translatesConcurrentDuplicateEmployeeConstraintFailure() {
        var cause = new ConstraintViolationException("duplicate",
                new SQLException("duplicate", "23505"), "uq_user_accounts_employee");
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint", cause));
        assertCode(EMPLOYEE_ALREADY_HAS_ACCOUNT, () -> service.createAccount(validRequest()));
    }

    @Test
    void doesNotMislabelUnexpectedDatabaseFailureAsDuplicateAccount() {
        var error = new DataIntegrityViolationException("unrelated database failure");
        when(accounts.saveAndFlush(any())).thenThrow(error);
        assertSame(error, assertThrows(DataIntegrityViolationException.class,
                () -> service.createAccount(validRequest())));
    }

    @Test
    void rejectsEnablingAccountForInactiveEmployee() {
        UserAccount account = existingAccount();
        employee.setActive(false);
        assertCode(EMPLOYEE_INACTIVE, () -> service.enableAccount(1L));
        assertFalse(account.isEnabled());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEnablingAccountWithoutRoles() {
        UserAccount account = existingAccount();
        account.removeRole(cashier);
        assertCode(ACCOUNT_HAS_NO_ROLES, () -> service.enableAccount(1L));
        assertFalse(account.isEnabled());
    }

    @Test
    void enablesAndDisablesExistingAccount() {
        existingAccount();
        assertTrue(service.enableAccount(1L).enabled());
        assertFalse(service.disableAccount(1L).enabled());
    }

    @Test
    void roleReplacementDoesNotPartiallyMutateWhenARoleIsMissing() {
        UserAccount account = existingAccount();
        assertCode(UNKNOWN_ROLE, () -> service.replaceRoles(1L,
                new AccountRolesUpdateRequest(Set.of("CASHIER", "UNKNOWN"))));
        assertEquals(Set.of(cashier), account.getRoles());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void replacesRolesAndReturnsOnlyNewRoleCodes() {
        UserAccount account = existingAccount();
        Role technician = new Role("TECHNICIAN", "Technician");
        when(roles.findByCode("TECHNICIAN")).thenReturn(Optional.of(technician));
        var response = service.replaceRoles(1L, new AccountRolesUpdateRequest(Set.of("TECHNICIAN")));
        assertEquals(Set.of("TECHNICIAN"), response.roleCodes());
        assertEquals(Set.of(technician), account.getRoles());
    }

    @Test
    void rejectsInvalidOrMissingAccountIds() {
        assertCode(INVALID_REQUEST, () -> service.getAccount(null));
        assertCode(INVALID_REQUEST, () -> service.disableAccount(0L));
        assertCode(ACCOUNT_NOT_FOUND, () -> service.getAccount(100L));
    }

    @Test
    void readsAccountWithoutSaving() {
        existingAccount();
        assertEquals("cashier.one", service.getAccount(1L).username());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void requestToStringRedactsPassword() {
        assertFalse(validRequest().toString().contains(RAW_PASSWORD));
        assertTrue(validRequest().toString().contains("[REDACTED]"));
    }

    @Test
    void realPasswordEncoderUsesSaltAndVerifiesWithoutPlaintextFallback() {
        PasswordEncoder realEncoder = new PasswordConfig().passwordEncoder();
        String first = realEncoder.encode(RAW_PASSWORD);
        String second = realEncoder.encode(RAW_PASSWORD);
        assertTrue(first.startsWith("{bcrypt}"));
        assertNotEquals(first, second);
        assertTrue(realEncoder.matches(RAW_PASSWORD, first));
        assertFalse(realEncoder.matches("wrong password", first));
        assertThrows(IllegalArgumentException.class,
                () -> realEncoder.matches(RAW_PASSWORD, "{noop}" + RAW_PASSWORD));
    }

    private UserAccount existingAccount() {
        UserAccount account = new UserAccount(employee, "cashier.one", "{bcrypt}test-encoded-value");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.assignRole(cashier);
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        return account;
    }

    private AccountCreateRequest validRequest() {
        return request("cashier.one", RAW_PASSWORD, Set.of("CASHIER"));
    }

    private AccountCreateRequest request(String username, String password, Set<String> roleCodes) {
        return new AccountCreateRequest(EMPLOYEE_ID, username, password, roleCodes);
    }

    private void assertCode(AccountOperationException.Code expected, Runnable operation) {
        assertEquals(expected, assertThrows(AccountOperationException.class, operation::run).getCode());
    }
}
