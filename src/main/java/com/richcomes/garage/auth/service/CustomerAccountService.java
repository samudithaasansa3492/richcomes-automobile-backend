package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.dto.CustomerAccountCreateRequest;
import com.richcomes.garage.auth.dto.CustomerAccountResponse;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.repository.CustomerRepository;
import java.nio.charset.StandardCharsets;
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

    public CustomerAccountService(UserAccountRepository accounts, CustomerRepository customers,
            RoleRepository roles, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.customers = customers;
        this.roles = roles;
        this.passwords = passwords;
    }

    public CustomerAccountResponse register(CustomerAccountCreateRequest request) {
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
            return response(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException conflict) {
            // DB unique constraints protect concurrent account enrollment.
            throw failure(ACCOUNT_CONFLICT, "Customer account could not be created; check for an existing account");
        }
    }

    public CustomerAccountResponse activate(Long accountId) {
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
        account.enable();
        return response(accounts.saveAndFlush(account));
    }

    private CustomerAccountResponse response(UserAccount account) {
        return new CustomerAccountResponse(account.getId(), account.getCustomer().getId(),
                account.getUsername(), account.isEnabled());
    }

    private AccountOperationException failure(AccountOperationException.Code code, String message) {
        return new AccountOperationException(code, message);
    }
}
