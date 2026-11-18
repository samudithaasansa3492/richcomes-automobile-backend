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
import java.security.SecureRandom;
import java.util.Base64;
import com.richcomes.garage.auth.recovery.RecoveryService;
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
    private final RecoveryService recovery;
    private final SecureRandom random = new SecureRandom();
    // Independent rollout gate: staff recovery SMTP being enabled does not
    // authorize the real-world customer identity proofing process.
    @org.springframework.beans.factory.annotation.Value("${CUSTOMER_ONBOARDING_ENABLED:false}")
    private boolean onboardingEnabled;

    public CustomerAccountService(UserAccountRepository accounts, CustomerRepository customers,
            RoleRepository roles, PasswordEncoder passwords, SecurityAuditRepository audit,
            RecoveryService recovery) {
        this.accounts = accounts;
        this.customers = customers;
        this.roles = roles;
        this.passwords = passwords;
        this.audit = audit;
        this.recovery = recovery;
    }

    public CustomerAccountResponse register(CustomerAccountCreateRequest request, AccountPrincipal actor) {
        requireRegistrationActor(actor);
        requireOnboardingEnabled();
        if (request == null || request.customerId() == null || request.customerId() <= 0
                || request.username() == null || request.username().isBlank()) {
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
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        // Never accept a staff-selected secret: only a cryptographically random
        // unknown placeholder exists until a verified customer sets a password.
        var unusableSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var account = UserAccount.customer(customer, username, passwords.encode(unusableSecret), customerRole);
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

    /**
     * OWNER attests an in-person proof that links the known customer record to
     * the person receiving the setup email. The case reference is non-sensitive.
     * Actual physical identity checks are an operational responsibility.
     */
    public void verifyAndInvite(Long accountId, String caseReference, AccountPrincipal actor) {
        requireActivationActor(actor);
        requireOnboardingEnabled();
        if (accountId == null || accountId <= 0 || caseReference == null
                || !caseReference.matches("[A-Z0-9][A-Z0-9_-]{7,63}")) {
            throw failure(INVALID_REQUEST, "Valid verification case reference required");
        }
        var account = accounts.lockById(accountId)
                .orElseThrow(() -> failure(CUSTOMER_ACCOUNT_NOT_FOUND, "Customer login account not found"));
        if (!account.isCustomer() || account.getCustomer() == null
                || account.getEmployee() != null || account.getRoles().size() != 1
                || account.getRoles().stream().noneMatch(role -> "CUSTOMER".equals(role.getCode()))
                || account.isEnabled() || account.hasCustomerChosenPassword()) {
            throw failure(CUSTOMER_VERIFICATION_REQUIRED, "Customer is not eligible for invitation");
        }
        if (account.getCustomer().getStatus() != CustomerStatus.ACTIVE) {
            throw failure(CUSTOMER_INACTIVE, "Cannot invite an inactive customer");
        }
        var email = account.getCustomer().getEmail();
        if (email == null || !email.strip().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw failure(INVALID_REQUEST, "An approved customer email is required");
        }
        account.recordCustomerIdentityProof(actor.accountId(), caseReference, email, Instant.now());
        accounts.saveAndFlush(account);
        recovery.inviteCustomer(account, actor.accountId());
    }

    public CustomerAccountResponse activate(Long accountId, AccountPrincipal actor) {
        // Enrollment may be delegated to CASHIER. Enabling a customer login is a
        // higher-trust step reserved for OWNER after out-of-band identity checks.
        // This is intentionally enforced here as well as in the HTTP filter.
        requireActivationActor(actor);
        requireOnboardingEnabled();
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
        if (!account.hasCustomerVerifiedIdentity()) {
            throw failure(CUSTOMER_VERIFICATION_REQUIRED, "Customer identity verification is required");
        }
        if (!account.hasCustomerChosenPassword()) {
            throw failure(CUSTOMER_PASSWORD_NOT_SET, "Customer must set their own password first");
        }
        if (!account.isEnabled()) {
            account.enable();
            accounts.saveAndFlush(account);
            audit.save(new SecurityAuditEvent(actor.accountId(), account.getId(),
                    "CUSTOMER_ACCOUNT_ACTIVATED", Instant.now()));
        }
        return response(account);
    }

    private void requireOnboardingEnabled() {
        if (!onboardingEnabled) {
            throw failure(CUSTOMER_ONBOARDING_UNAVAILABLE,
                    "Customer onboarding is unavailable until verified rollout approval");
        }
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
