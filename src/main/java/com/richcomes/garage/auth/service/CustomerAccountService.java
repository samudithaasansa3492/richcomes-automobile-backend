package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.CustomerAccountCreateRequest;
import com.richcomes.garage.auth.dto.CustomerAccountResponse;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.recovery.SecurityAuditEvent;
import com.richcomes.garage.auth.recovery.SecurityAuditRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.repository.CustomerRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.richcomes.garage.auth.exception.AccountOperationException.Code.*;

/** Staff-only customer enrollment. No customer can enroll or enable itself. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CustomerAccountService {
    private final UserAccountRepository accounts;
    private final CustomerRepository customers;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final SecurityAuditRepository audit;

    public CustomerAccountService(UserAccountRepository accounts, CustomerRepository customers,
            RoleRepository roles, PasswordEncoder passwords, SecurityAuditRepository audit) {
        this.accounts = accounts;
        this.customers = customers;
        this.roles = roles;
        this.passwords = passwords;
        this.audit = audit;
    }

    public CustomerAccountResponse register(CustomerAccountCreateRequest request, AccountPrincipal actor) {
        requireRegistrationActor(actor);
        if (request == null || request.customerId() == null || request.customerId() <= 0
                || request.username() == null || request.password() == null
                || request.password().length() < 15 || request.password().length() > 72
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || request.password().isBlank()) {
            throw failure(INVALID_REQUEST, "Invalid customer enrollment request");
        }
        final String username;
        try {
            username = UserAccount.normalizeUsername(request.username());
        } catch (IllegalArgumentException exception) {
            throw failure(INVALID_REQUEST, "Invalid username format");
        }
        var customer = customers.findById(request.customerId())
                .orElseThrow(() -> failure(CUSTOMER_NOT_FOUND, "Customer record not found"));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw failure(CUSTOMER_INACTIVE, "Customer must be active before enrollment");
        }
        if (accounts.existsByCustomer_Id(customer.getId())) {
            throw failure(CUSTOMER_ALREADY_HAS_ACCOUNT, "Customer already has a login account");
        }
        if (accounts.existsByUsername(username)) {
            throw failure(USERNAME_ALREADY_EXISTS, "Username already exists");
        }
        var customerRole = roles.findByCode("CUSTOMER")
                .orElseThrow(() -> failure(CUSTOMER_ROLE_NOT_CONFIGURED, "Customer role is not configured"));
        var account = UserAccount.customer(customer, username, passwords.encode(request.password()), customerRole);
        // Keep it disabled until trusted staff separately verifies identity and activates.
        try {
            var saved = accounts.saveAndFlush(account);
            audit.save(new SecurityAuditEvent(actor.accountId(), saved.getId(),
                    "CUSTOMER_ACCOUNT_CREATED_DISABLED", Instant.now()));
            return response(saved);
        } catch (DataIntegrityViolationException conflict) {
            // DB unique constraints protect concurrent account enrollment.
            throw failure(ACCOUNT_CONFLICT, "Customer account could not be created; check for an existing account");
        }
    }

    public CustomerAccountResponse activate(Long accountId, AccountPrincipal actor) {
        // Enrollment may be delegated to CASHIER. Enabling a customer login is a
        // higher-trust step reserved for OWNER after out-of-band identity checks.
        // This is intentionally enforced here as well as in the HTTP filter.
        requireActivationActor(actor);
        if (accountId == null || accountId <= 0) {
            throw failure(INVALID_REQUEST, "Valid account ID required");
        }
        var account = accounts.findOneById(accountId)
                .orElseThrow(() -> failure(CUSTOMER_ACCOUNT_NOT_FOUND, "Customer login account not found"));
        if (!account.isCustomer() || account.getCustomer() == null
                || account.getEmployee() != null || account.getRoles().size() != 1
                || account.getRoles().stream().noneMatch(role -> "CUSTOMER".equals(role.getCode()))) {
            throw failure(CUSTOMER_ACCOUNT_NOT_FOUND, "Customer login account not found");
        }
        if (account.getCustomer().getStatus() != CustomerStatus.ACTIVE) {
            throw failure(CUSTOMER_INACTIVE, "Cannot activate a suspended customer");
        }
        if (!account.isEnabled()) {
            account.enable();
            accounts.saveAndFlush(account);
            audit.save(new SecurityAuditEvent(actor.accountId(), account.getId(),
                    "CUSTOMER_ACCOUNT_ACTIVATED", Instant.now()));
        }
        return response(account);
    }

    private void requireActivationActor(AccountPrincipal actor) {
        if (actor == null || actor.accountId() == null || !actor.roles().contains("OWNER")
                || !actor.permissions().contains("customer:register")) {
            throw failure(CUSTOMER_REGISTRATION_NOT_ALLOWED,
                    "Only an authorized OWNER can activate a customer login");
        }
    }

    private void requireRegistrationActor(AccountPrincipal actor) {
        if (actor == null || actor.accountId() == null || !actor.permissions().contains("customer:register")
                || actor.roles().stream().noneMatch(role -> role.equals("OWNER") || role.equals("CASHIER"))) {
            throw failure(CUSTOMER_REGISTRATION_NOT_ALLOWED, "Customer registration permission required");
        }
    }

    private CustomerAccountResponse response(UserAccount account) {
        return new CustomerAccountResponse(account.getId(), account.getCustomer().getId(),
                account.getUsername(), account.isEnabled());
    }

    private AccountOperationException failure(AccountOperationException.Code code, String message) {
        return new AccountOperationException(code, message);
    }
}
