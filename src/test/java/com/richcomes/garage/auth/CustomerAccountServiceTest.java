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
    private final AccountPrincipal staff = new AccountPrincipal(51L, null, "owner.one",
            Set.of("OWNER"), Set.of("customer:register"));
    private CustomerAccountService service;
    private Customer customer;
    private static final String PASSWORD = "synthetic secret password";

    @BeforeEach void init() {
        service = new CustomerAccountService(accounts, customers, roles, passwords, audit);
        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 7L);
        customer.setStatus(CustomerStatus.ACTIVE);
    }

    private CustomerAccountCreateRequest request() {
        return new CustomerAccountCreateRequest(7L, "  CUSTOMER.One ", PASSWORD);
    }

    @Test void createsDisabledAccountForExistingActiveCustomer() {
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER", "Customer")));
        when(passwords.encode(PASSWORD)).thenReturn("encoded-password");
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
        when(accounts.findOneById(4L)).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        assertFalse(account.isEnabled());
        assertTrue(service.activate(4L, staff).enabled());
        assertTrue(account.isEnabled());
    }

    @Test void rejectsWeakPasswordBeforeDatabaseAccess() {
        var bad = new CustomerAccountCreateRequest(7L, "customer.one", "short");
        assertEquals(AccountOperationException.Code.INVALID_REQUEST,
                assertThrows(AccountOperationException.class, () -> service.register(bad, staff)).getCode());
        verifyNoInteractions(accounts, customers, roles, passwords);
    }
}
