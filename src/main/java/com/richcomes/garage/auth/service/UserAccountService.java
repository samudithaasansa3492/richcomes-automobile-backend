package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.AccountCreateRequest;
import com.richcomes.garage.auth.dto.AccountPageResponse;
import com.richcomes.garage.auth.dto.AccountResponse;
import com.richcomes.garage.auth.dto.AccountRolesUpdateRequest;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.employee.repository.EmployeeRepository;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;

/**
 * Account operations behind permission-protected HTTP endpoints.
 * Access changes share a database lock to preserve the last active owner.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class UserAccountService {

    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final AdminAccessGuard adminAccess;

    public UserAccountService(UserAccountRepository accounts, EmployeeRepository employees,
                              RoleRepository roles, PasswordEncoder passwordEncoder, Validator validator,
                              AdminAccessGuard adminAccess) {
        this.accounts = accounts;
        this.employees = employees;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.adminAccess = adminAccess;
    }

    public AccountResponse createAccount(AccountCreateRequest request) {
        validate(request);
        adminAccess.lockAdministration();
        String username;
        try {
            username = UserAccount.normalizeUsername(request.username());
        } catch (IllegalArgumentException exception) {
            throw failure(INVALID_REQUEST, "Invalid username format");
        }
        // BCrypt's limit is bytes, not Java characters. Never silently truncate passwords.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw failure(INVALID_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        var employee = employees.findById(request.employeeId())
                .orElseThrow(() -> failure(EMPLOYEE_NOT_FOUND, "Employee not found"));
        requireLoginEmployee(employee);
        if (accounts.existsByEmployee_Id(employee.getId())) {
            throw failure(EMPLOYEE_ALREADY_HAS_ACCOUNT, "Employee already has an account");
        }
        if (accounts.existsByUsername(username)) {
            throw failure(USERNAME_ALREADY_EXISTS, "Username already exists");
        }
        Set<Role> assignedRoles = resolveRoles(request.roleCodes());
        UserAccount account = new UserAccount(employee, username, passwordEncoder.encode(request.password()));
        assignedRoles.forEach(account::assignRole);
        try {
            return toResponse(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            // Pre-checks improve feedback; unique constraints also protect concurrent creations.
            String constraint = constraintName(exception);
            if ("uq_user_accounts_username".equals(constraint)) {
                throw failure(USERNAME_ALREADY_EXISTS, "Username already exists");
            }
            if ("uq_user_accounts_employee".equals(constraint)) {
                throw failure(EMPLOYEE_ALREADY_HAS_ACCOUNT, "Employee already has an account");
            }
            if ("fk_user_accounts_employee".equals(constraint)) {
                throw failure(ACCOUNT_CONFLICT, "Employee changed during account creation; retry the operation");
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        return toResponse(findAccount(accountId));
    }

    @Transactional(readOnly = true)
    public AccountPageResponse listAccounts(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw failure(INVALID_REQUEST, "Page must be nonnegative and size must be between 1 and 100");
        }
        // Page roots only; collection fetch joins would paginate in memory.
        var result = accounts.findAll(PageRequest.of(
                page, size, Sort.by("id")));
        return new AccountPageResponse(
                result.getContent().stream().map(this::toResponse).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public AccountResponse replaceRoles(Long accountId, AccountRolesUpdateRequest request, Long actorId) {
        validate(request);
        adminAccess.rejectSelf(accountId, actorId);
        adminAccess.lockAdministration();
        UserAccount account = findAccount(accountId);
        requireBusinessTarget(account);
        requireLoginEmployee(account.getEmployee());
        Set<Role> replacements = resolveRoles(request.roleCodes());
        if (replacements.stream().noneMatch(role -> "OWNER".equals(role.getCode()))) {
            adminAccess.protectOwner(account);
        }
        // Resolve all codes before changing managed state.
        new HashSet<>(account.getRoles()).forEach(account::removeRole);
        replacements.forEach(account::assignRole);
        return toResponse(accounts.saveAndFlush(account));
    }

    public AccountResponse enableAccount(Long accountId) {
        adminAccess.lockAdministration();
        UserAccount account = findAccount(accountId);
        requireBusinessTarget(account);
        requireLoginEmployee(account.getEmployee());
        if (!account.getEmployee().isActive()) {
            throw failure(EMPLOYEE_INACTIVE, "Cannot enable an account for an inactive employee");
        }
        if (account.getRoles().isEmpty()) {
            throw failure(ACCOUNT_HAS_NO_ROLES, "Assign at least one role before enabling an account");
        }
        resolveRoles(account.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()));
        account.enable();
        return toResponse(accounts.saveAndFlush(account));
    }

    public AccountResponse disableAccount(Long accountId, Long actorId) {
        adminAccess.rejectSelf(accountId, actorId);
        adminAccess.lockAdministration();
        UserAccount account = findAccount(accountId);
        requireBusinessTarget(account);
        adminAccess.protectOwner(account);
        account.disable();
        return toResponse(accounts.saveAndFlush(account));
    }

    private UserAccount findAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw failure(INVALID_REQUEST, "Account ID must be positive");
        }
        return accounts.findById(accountId)
                .orElseThrow(() -> failure(ACCOUNT_NOT_FOUND, "Account not found"));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> result = new LinkedHashSet<>();
        for (String code : codes) {
            if (!Set.of("OWNER", "CASHIER").contains(code)) {
                throw failure(UNKNOWN_ROLE, "Only OWNER and CASHIER roles can be assigned through business account APIs");
            }
            result.add(roles.findByCode(code)
                    .orElseThrow(() -> failure(UNKNOWN_ROLE, "One or more role codes do not exist")));
        }
        return result;
    }

    private void requireLoginEmployee(com.richcomes.garage.employee.entity.Employee employee) {
        if (employee.getPosition() == com.richcomes.garage.employee.entity.EmployeePosition.TECHNICIAN) {
            throw failure(INVALID_REQUEST, "Technicians cannot have system accounts");
        }
    }

    private void requireBusinessTarget(UserAccount account) {
        if (account.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))) {
            throw failure(TECHNICAL_ACCOUNT_PROTECTED, "Technical accounts cannot be managed through business account APIs");
        }
    }

    private <T> void validate(T request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            // Do not expose ConstraintViolation objects: they can contain the raw password.
            throw failure(INVALID_REQUEST, "Invalid account request");
        }
    }

    private AccountResponse toResponse(UserAccount account) {
        return new AccountResponse(account.getId(), account.getEmployee().getId(), account.getUsername(),
                account.isEnabled(), account.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                account.getCreatedAt(), account.getUpdatedAt());
    }

    private String constraintName(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    private AccountOperationException failure(AccountOperationException.Code code, String message) {
        return new AccountOperationException(code, message);
    }
}
