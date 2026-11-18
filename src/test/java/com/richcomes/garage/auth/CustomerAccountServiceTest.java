package com.richcomes.garage.auth;

import com.richcomes.garage.auth.dto.CustomerAccountCreateRequest;
import com.richcomes.garage.auth.entity.Role;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.repository.RoleRepository;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.recovery.SecurityAuditRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import java.util.Set;
import com.richcomes.garage.auth.service.CustomerAccountService;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.repository.CustomerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceTest {
    @Mock UserAccountRepository accounts;
    @Mock CustomerRepository customers;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder passwords;
    @Mock SecurityAuditRepository audit;
    @Mock com.richcomes.garage.auth.recovery.RecoveryService recovery;
    private final AccountPrincipal staff = new AccountPrincipal(51L, null, "owner.one",
            Set.of("OWNER"), Set.of("customer:register"));
    private CustomerAccountService service;
    private Customer customer;
    private static final String PASSWORD = "synthetic secret password";

    @BeforeEach void init() {
        service = new CustomerAccountService(accounts, customers, roles, passwords, audit, recovery);
        ReflectionTestUtils.setField(service, "onboardingEnabled", true);
        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 7L);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setEmail("customer@example.invalid");
    }

    private CustomerAccountCreateRequest request() {
        return new CustomerAccountCreateRequest(7L, "  CUSTOMER.One ");
    }

    @Test void rolloutFlagOffDeniesEnrollmentAttestationAndActivationBeforeDatabaseAccess() {
        ReflectionTestUtils.setField(service, "onboardingEnabled", false);
        assertEquals(AccountOperationException.Code.CUSTOMER_ONBOARDING_UNAVAILABLE,
                assertThrows(AccountOperationException.class,
                        () -> service.register(request(), staff)).getCode());
        assertEquals(AccountOperationException.Code.CUSTOMER_ONBOARDING_UNAVAILABLE,
                assertThrows(AccountOperationException.class,
                        () -> service.verifyAndInvite(4L, "CASE-00123", staff)).getCode());
        assertEquals(AccountOperationException.Code.CUSTOMER_ONBOARDING_UNAVAILABLE,
                assertThrows(AccountOperationException.class,
                        () -> service.activate(4L, staff)).getCode());
        verifyNoInteractions(accounts, customers, roles, passwords, audit, recovery);
    }

    @Test void createsDisabledAccountForExistingActiveCustomer() {
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER", "Customer")));
        when(passwords.encode(anyString())).thenReturn("encoded-password");
        when(accounts.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        var result = service.register(request(), staff);
        assertEquals(7L, result.customerId());
        assertEquals("customer.one", result.username());
        assertFalse(result.enabled());
        var captured = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(captured.capture());
        assertNull(captured.getValue().getEmployee());
        assertTrue(captured.getValue().isCustomer());
        assertEquals("CUSTOMER", captured.getValue().getRoles().iterator().next().getCode());
        assertEquals("encoded-password", captured.getValue().getPasswordHash());
    }

    @Test void rejectsExistingCustomerIdentity() {
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        when(accounts.existsByCustomer_Id(7L)).thenReturn(true);
        var ex = assertThrows(AccountOperationException.class, () -> service.register(request(), staff));
        assertEquals(AccountOperationException.Code.CUSTOMER_ALREADY_HAS_ACCOUNT, ex.getCode());
        verifyNoInteractions(passwords);
    }

    @Test void cannotRegisterSuspendedCustomer() {
        customer.setStatus(CustomerStatus.SUSPENDED);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        assertEquals(AccountOperationException.Code.CUSTOMER_INACTIVE,
                assertThrows(AccountOperationException.class,
                        () -> service.register(request(), staff)).getCode());
        verifyNoInteractions(passwords);
    }

    @Test void activationIsSeparateAndRequiresCustomerIdentity() {
        var account = UserAccount.customer(customer, "customer.one", "encoded-password",
                new Role("CUSTOMER", "Customer"));
        account.recordCustomerIdentityProof(51L, "CASE-00123", customer.getEmail(),
                java.time.Instant.now());
        account.setCustomerInitialPassword("encoded-customer-secret", java.time.Instant.now());
        when(accounts.findOneById(4L)).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        assertFalse(account.isEnabled());
        assertTrue(service.activate(4L, staff).enabled());
        assertTrue(account.isEnabled());
    }

    @Test void delegatedCashierCanEnrollButCannotActivateCustomerLogin() {
        var cashier = new AccountPrincipal(61L, null, "cashier.one",
                Set.of("CASHIER"), Set.of("customer:register"));
        var denied = assertThrows(AccountOperationException.class,
                () -> service.activate(4L, cashier));
        assertEquals(AccountOperationException.Code.CUSTOMER_REGISTRATION_NOT_ALLOWED,
                denied.getCode());
        verifyNoInteractions(accounts, customers, roles, passwords, audit);
    }

    @Test void ownerWithoutRegistrationAuthorityCannotActivateCustomerLogin() {
        var missingPermission = new AccountPrincipal(51L, null, "owner.one",
                Set.of("OWNER"), Set.of());
        var denied = assertThrows(AccountOperationException.class,
                () -> service.activate(4L, missingPermission));
        assertEquals(AccountOperationException.Code.CUSTOMER_REGISTRATION_NOT_ALLOWED,
                denied.getCode());
        verifyNoInteractions(accounts, customers, roles, passwords, audit);
    }

    @Test void customerActivationDeniedUntilProofAndCustomerChosenPassword() {
        var account = UserAccount.customer(customer, "customer.one", "random-placeholder-hash",
                new Role("CUSTOMER", "Customer"));
        when(accounts.findOneById(4L)).thenReturn(Optional.of(account));
        assertEquals(AccountOperationException.Code.CUSTOMER_VERIFICATION_REQUIRED,
                assertThrows(AccountOperationException.class,
                        () -> service.activate(4L, staff)).getCode());
        account.recordCustomerIdentityProof(51L, "CASE-00123", customer.getEmail(),
                java.time.Instant.now());
        assertEquals(AccountOperationException.Code.CUSTOMER_PASSWORD_NOT_SET,
                assertThrows(AccountOperationException.class,
                        () -> service.activate(4L, staff)).getCode());
        verify(accounts, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void ownerRecordsProofAndInvitesWithoutEnablingCustomer() {
        var account = UserAccount.customer(customer, "customer.one", "random-placeholder-hash",
                new Role("CUSTOMER", "Customer"));
        when(accounts.lockById(4L)).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        service.verifyAndInvite(4L, "CASE-00123", staff);
        assertTrue(account.hasCustomerVerifiedIdentity());
        assertFalse(account.isEnabled());
        assertFalse(account.hasCustomerChosenPassword());
        verify(recovery).inviteCustomer(account, staff.accountId());
    }

    @Test void delegatedCashierCannotAttestCustomerProof() {
        var cashier = new AccountPrincipal(61L, null, "cashier.one",
                Set.of("CASHIER"), Set.of("customer:register"));
        var denied = assertThrows(AccountOperationException.class,
                () -> service.verifyAndInvite(4L, "CASE-00123", cashier));
        assertEquals(AccountOperationException.Code.CUSTOMER_REGISTRATION_NOT_ALLOWED,
                denied.getCode());
        verifyNoInteractions(accounts, recovery);
    }

    @Test void rejectsInvalidUsernameBeforeDatabaseAccess() {
        var bad = new CustomerAccountCreateRequest(7L, "no spaces allowed");
        assertEquals(AccountOperationException.Code.INVALID_REQUEST,
                assertThrows(AccountOperationException.class, () -> service.register(bad, staff)).getCode());
        verifyNoInteractions(accounts, customers, roles, passwords);
    }
}
