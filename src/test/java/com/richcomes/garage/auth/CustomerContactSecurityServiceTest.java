package com.richcomes.garage.auth;

import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.recovery.RecoveryChallengeRepository;
import com.richcomes.garage.auth.recovery.SecurityAuditEvent;
import com.richcomes.garage.auth.recovery.SecurityAuditRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.security.AccountAccessPolicy;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.service.CustomerContactSecurityService;
import com.richcomes.garage.customer.entity.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerContactSecurityServiceTest {
    @Mock UserAccountRepository accounts;
    @Mock RecoveryChallengeRepository challenges;
    @Mock SecurityAuditRepository audit;
    @Mock PasswordEncoder passwords;
    @Mock EntityManager manager;
    private final Instant now = Instant.parse("2026-11-27T00:00:00Z");
    private final AccountPrincipal owner = new AccountPrincipal(51L, null, "owner.one",
            Set.of("OWNER"), Set.of("customer:manage"));
    private Customer customer;
    private UserAccount account;
    private CustomerContactSecurityService service;

    @BeforeEach void init() {
        service = new CustomerContactSecurityService(accounts, challenges, audit, passwords, manager,
                Clock.fixed(now, ZoneOffset.UTC));
        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 7L);
        customer.setEmail("old@example.invalid");
        account = UserAccount.customer(customer, "customer.one", "initial-placeholder",
                new Role("CUSTOMER", "Customer"));
        ReflectionTestUtils.setField(account, "id", 42L);
        account.recordCustomerIdentityProof(51L, "CASE-00123", customer.getEmail(), now);
        account.setCustomerInitialPassword("old-password-hash", now);
        account.verifyRecoveryEmail("recovery@example.invalid");
        account.enable();
    }

    private void locked() {
        when(manager.contains(customer)).thenReturn(true);
        when(manager.getLockMode(customer)).thenReturn(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void changedMailboxRevokesProofPasswordRecoveryAndTokensEvenIfOldAddressReturns() {
        locked();
        when(accounts.lockByCustomerId(7L)).thenReturn(Optional.of(account));
        when(passwords.encode(anyString())).thenReturn("unknown-replacement-hash");
        long before = account.getCredentialVersion();
        assertTrue(AccountAccessPolicy.canAuthenticate(account));
        service.invalidateBeforeEmailChange(customer, "new@example.invalid", owner);
        customer.setEmail("new@example.invalid");
        customer.setEmail("old@example.invalid");
        assertFalse(account.isEnabled());
        assertFalse(account.hasCustomerVerifiedIdentity());
        assertFalse(account.hasCustomerChosenPassword());
        assertFalse(AccountAccessPolicy.canAuthenticate(account));
        assertNull(account.getRecoveryEmail());
        assertEquals(before + 1, account.getCredentialVersion());
        assertEquals("unknown-replacement-hash", account.getPasswordHash());
        verify(accounts).saveAndFlush(account);
        verify(challenges).deleteByAccountId(42L);
        verify(challenges).flush();
        verify(audit).save(any(SecurityAuditEvent.class));
    }

    @Test void unchangedNormalizedMailboxDoesNotRevokeAnything() {
        locked();
        service.invalidateBeforeEmailChange(customer, " OLD@example.invalid ", owner);
        assertTrue(AccountAccessPolicy.canAuthenticate(account));
        verifyNoInteractions(accounts, challenges, audit, passwords);
    }

    @Test void removingMailboxAlsoInvalidatesIdentity() {
        locked();
        when(accounts.lockByCustomerId(7L)).thenReturn(Optional.of(account));
        when(passwords.encode(anyString())).thenReturn("unknown-replacement-hash");
        service.invalidateBeforeEmailChange(customer, null, owner);
        assertFalse(account.hasCustomerVerifiedIdentity());
        verify(challenges).deleteByAccountId(42L);
    }

    @Test void noAccountDoesNotCreateIdentityOrAudit() {
        locked();
        when(accounts.lockByCustomerId(7L)).thenReturn(Optional.empty());
        service.invalidateBeforeEmailChange(customer, "new@example.invalid", owner);
        verifyNoInteractions(challenges, audit, passwords);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test void unauthorizedActorCannotInvalidateEvenWithForgedPermission() {
        var cashier = new AccountPrincipal(52L, null, "cashier.one", Set.of("CASHIER"),
                Set.of("customer:manage"));
        assertThrows(AccessDeniedException.class,
                () -> service.invalidateBeforeEmailChange(customer, "new@example.invalid", cashier));
        verifyNoInteractions(accounts, challenges, audit, passwords, manager);
    }

    @Test void unlockedCustomerCannotBeUpdatedByContract() {
        when(manager.contains(customer)).thenReturn(true);
        when(manager.getLockMode(customer)).thenReturn(LockModeType.NONE);
        assertThrows(IllegalStateException.class,
                () -> service.invalidateBeforeEmailChange(customer, "new@example.invalid", owner));
        verifyNoInteractions(accounts, challenges, audit, passwords);
    }
}
