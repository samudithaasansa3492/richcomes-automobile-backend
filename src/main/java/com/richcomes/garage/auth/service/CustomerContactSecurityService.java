package com.richcomes.garage.auth.service;

import com.richcomes.garage.auth.recovery.RecoveryChallengeRepository;
import com.richcomes.garage.auth.recovery.SecurityAuditEvent;
import com.richcomes.garage.auth.recovery.SecurityAuditRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.customer.entity.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal contract for Member 02's transactional customer-contact update.
 * Not an HTTP endpoint. Never accept the actor from a request body. */
@Service
public class CustomerContactSecurityService {
    private final UserAccountRepository accounts;
    private final RecoveryChallengeRepository challenges;
    private final SecurityAuditRepository audit;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CustomerContactSecurityService(UserAccountRepository accounts,
            RecoveryChallengeRepository challenges, SecurityAuditRepository audit,
            PasswordEncoder passwords, EntityManager entityManager, Clock authClock) {
        this.accounts = accounts;
        this.challenges = challenges;
        this.audit = audit;
        this.passwords = passwords;
        this.entityManager = entityManager;
        this.clock = authClock;
    }

    /**
     * Caller must load the Customer with CustomerRepository.lockById in the
     * SAME transaction, before modifying any contact fields; pass the effective
     * new email (including null for removal). Only then update contact fields
     * and reset contact verification/consent in that transaction.
     *
     * Customer row first, existing account row second. Registration also locks
     * the customer, while recovery/invitation/activation serialize on the account.
     * A genuine mailbox change permanently invalidates old proof, even A->B->A.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateBeforeEmailChange(Customer lockedCustomer, String newEmail,
            AccountPrincipal actor) {
        if (actor == null || actor.accountId() == null || actor.accountId() <= 0
                || !actor.roles().contains("OWNER") || actor.roles().contains("ADMIN")
                || !actor.permissions().contains("customer:manage")) {
            throw new AccessDeniedException("Authorized OWNER required for customer contact change");
        }
        if (lockedCustomer == null || lockedCustomer.getId() == null
                || !entityManager.contains(lockedCustomer)
                || !Set.of(LockModeType.PESSIMISTIC_WRITE, LockModeType.PESSIMISTIC_FORCE_INCREMENT)
                        .contains(entityManager.getLockMode(lockedCustomer))) {
            throw new IllegalStateException("Customer contact change requires a managed write-locked customer");
        }
        if (Objects.equals(normalize(lockedCustomer.getEmail()), normalize(newEmail))) return;

        accounts.lockByCustomerId(lockedCustomer.getId()).ifPresent(account -> {
            if (!account.isCustomer() || account.getCustomer() == null
                    || !lockedCustomer.getId().equals(account.getCustomer().getId())) {
                throw new IllegalStateException("Invalid linked customer identity");
            }
            // Unknown placeholder; never let staff choose a replacement password.
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String unusableHash = passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            account.invalidateCustomerIdentityForEmailChange(unusableHash);
            accounts.saveAndFlush(account);
            challenges.deleteByAccountId(account.getId());
            challenges.flush();
            audit.save(new SecurityAuditEvent(actor.accountId(), account.getId(),
                    "CUSTOMER_EMAIL_CHANGED_IDENTITY_REVOKED", clock.instant()));
        });
    }

    private static String normalize(String email) {
        return email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
