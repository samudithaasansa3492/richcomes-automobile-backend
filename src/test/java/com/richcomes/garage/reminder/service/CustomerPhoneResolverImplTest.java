package com.richcomes.garage.reminder.service;

import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerPhoneResolverImplTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private CustomerPhoneResolverImpl resolver;

    private Customer activeCustomer;

    @BeforeEach
    void setUp() {
        activeCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(activeCustomer, 1L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(activeCustomer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field mobileField = Customer.class.getDeclaredField("mobile");
            mobileField.setAccessible(true);
            mobileField.set(activeCustomer, "0771234567");
            java.lang.reflect.Field emailField = Customer.class.getDeclaredField("email");
            emailField.setAccessible(true);
            emailField.set(activeCustomer, "customer@example.com");
            // Set new consent/verification fields
            java.lang.reflect.Field smsConsentField = Customer.class.getDeclaredField("smsConsent");
            smsConsentField.setAccessible(true);
            smsConsentField.set(activeCustomer, true);
            java.lang.reflect.Field mobileVerifiedField = Customer.class.getDeclaredField("mobileVerified");
            mobileVerifiedField.setAccessible(true);
            mobileVerifiedField.set(activeCustomer, true);
            java.lang.reflect.Field emailConsentField = Customer.class.getDeclaredField("emailConsent");
            emailConsentField.setAccessible(true);
            emailConsentField.set(activeCustomer, true);
            java.lang.reflect.Field emailVerifiedField = Customer.class.getDeclaredField("emailVerified");
            emailVerifiedField.setAccessible(true);
            emailVerifiedField.set(activeCustomer, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =====================================================
    // PHONE RESOLUTION TESTS
    // =====================================================

    @Test
    void phoneForCustomer_withValidActiveCustomer_returnsPhone() {
        when(customerService.getCustomerEntityById(1L)).thenReturn(activeCustomer);

        String phone = resolver.phoneForCustomer(1L);

        assertEquals("0771234567", phone);
    }

    @Test
    void phoneForCustomer_withNullCustomerId_returnsNull() {
        String phone = resolver.phoneForCustomer(null);
        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withZeroCustomerId_returnsNull() {
        String phone = resolver.phoneForCustomer(0L);
        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withNegativeCustomerId_returnsNull() {
        String phone = resolver.phoneForCustomer(-1L);
        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withCustomerNotFound_returnsNull() {
        when(customerService.getCustomerEntityById(999L)).thenReturn(null);

        String phone = resolver.phoneForCustomer(999L);

        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withInactiveCustomer_returnsNull() {
        Customer inactiveCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(inactiveCustomer, 2L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(inactiveCustomer, CustomerStatus.SUSPENDED);
            java.lang.reflect.Field mobileField = Customer.class.getDeclaredField("mobile");
            mobileField.setAccessible(true);
            mobileField.set(inactiveCustomer, "0771234567");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(2L)).thenReturn(inactiveCustomer);

        String phone = resolver.phoneForCustomer(2L);

        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withNoMobileNumber_returnsNull() {
        Customer noMobileCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(noMobileCustomer, 3L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(noMobileCustomer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field mobileField = Customer.class.getDeclaredField("mobile");
            mobileField.setAccessible(true);
            mobileField.set(noMobileCustomer, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(3L)).thenReturn(noMobileCustomer);

        String phone = resolver.phoneForCustomer(3L);

        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withBlankMobileNumber_returnsNull() {
        Customer blankMobileCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(blankMobileCustomer, 4L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(blankMobileCustomer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field mobileField = Customer.class.getDeclaredField("mobile");
            mobileField.setAccessible(true);
            mobileField.set(blankMobileCustomer, "   ");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(4L)).thenReturn(blankMobileCustomer);

        String phone = resolver.phoneForCustomer(4L);

        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withInvalidMobileFormat_returnsNull() {
        Customer invalidMobileCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(invalidMobileCustomer, 5L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(invalidMobileCustomer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field mobileField = Customer.class.getDeclaredField("mobile");
            mobileField.setAccessible(true);
            mobileField.set(invalidMobileCustomer, "1234567890"); // Invalid format
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(5L)).thenReturn(invalidMobileCustomer);

        String phone = resolver.phoneForCustomer(5L);

        assertNull(phone);
    }

    @Test
    void phoneForCustomer_withValidPlus94Format_returnsPhone() {
        Customer plus94Customer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(plus94Customer, 6L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(plus94Customer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field mobileField = Customer.class.getDeclaredField("mobile");
            mobileField.setAccessible(true);
            mobileField.set(plus94Customer, "+94771234567"); // Valid +94 format
            // Add new consent/verification fields
            java.lang.reflect.Field smsConsentField = Customer.class.getDeclaredField("smsConsent");
            smsConsentField.setAccessible(true);
            smsConsentField.set(plus94Customer, true);
            java.lang.reflect.Field mobileVerifiedField = Customer.class.getDeclaredField("mobileVerified");
            mobileVerifiedField.setAccessible(true);
            mobileVerifiedField.set(plus94Customer, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(6L)).thenReturn(plus94Customer);

        String phone = resolver.phoneForCustomer(6L);

        assertEquals("+94771234567", phone);
    }

    @Test
    void phoneForCustomer_withServiceException_returnsNull() {
        when(customerService.getCustomerEntityById(1L))
                .thenThrow(new RuntimeException("Database error"));

        String phone = resolver.phoneForCustomer(1L);

        assertNull(phone);
    }

    // =====================================================
    // EMAIL RESOLUTION TESTS
    // =====================================================

    @Test
    void emailForCustomer_withValidActiveCustomer_returnsEmail() {
        // Need to set email consent/verification for the activeCustomer
        try {
            java.lang.reflect.Field emailConsentField = Customer.class.getDeclaredField("emailConsent");
            emailConsentField.setAccessible(true);
            emailConsentField.set(activeCustomer, true);
            java.lang.reflect.Field emailVerifiedField = Customer.class.getDeclaredField("emailVerified");
            emailVerifiedField.setAccessible(true);
            emailVerifiedField.set(activeCustomer, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(1L)).thenReturn(activeCustomer);

        String email = resolver.emailForCustomer(1L);

        assertEquals("customer@example.com", email);
    }

    @Test
    void emailForCustomer_withNullCustomerId_returnsNull() {
        String email = resolver.emailForCustomer(null);
        assertNull(email);
    }

    @Test
    void emailForCustomer_withCustomerNotFound_returnsNull() {
        when(customerService.getCustomerEntityById(999L)).thenReturn(null);

        String email = resolver.emailForCustomer(999L);

        assertNull(email);
    }

    @Test
    void emailForCustomer_withInactiveCustomer_returnsNull() {
        Customer inactiveCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(inactiveCustomer, 2L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(inactiveCustomer, CustomerStatus.SUSPENDED);
            java.lang.reflect.Field emailField = Customer.class.getDeclaredField("email");
            emailField.setAccessible(true);
            emailField.set(inactiveCustomer, "customer@example.com");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(2L)).thenReturn(inactiveCustomer);

        String email = resolver.emailForCustomer(2L);

        assertNull(email);
    }

    @Test
    void emailForCustomer_withNoEmail_returnsNull() {
        Customer noEmailCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(noEmailCustomer, 3L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(noEmailCustomer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field emailField = Customer.class.getDeclaredField("email");
            emailField.setAccessible(true);
            emailField.set(noEmailCustomer, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(3L)).thenReturn(noEmailCustomer);

        String email = resolver.emailForCustomer(3L);

        assertNull(email);
    }

    @Test
    void emailForCustomer_withInvalidEmailFormat_returnsNull() {
        Customer invalidEmailCustomer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(invalidEmailCustomer, 4L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(invalidEmailCustomer, CustomerStatus.ACTIVE);
            java.lang.reflect.Field emailField = Customer.class.getDeclaredField("email");
            emailField.setAccessible(true);
            emailField.set(invalidEmailCustomer, "not-an-email");
            // Add consent/verification fields
            java.lang.reflect.Field emailConsentField = Customer.class.getDeclaredField("emailConsent");
            emailConsentField.setAccessible(true);
            emailConsentField.set(invalidEmailCustomer, true);
            java.lang.reflect.Field emailVerifiedField = Customer.class.getDeclaredField("emailVerified");
            emailVerifiedField.setAccessible(true);
            emailVerifiedField.set(invalidEmailCustomer, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(customerService.getCustomerEntityById(4L)).thenReturn(invalidEmailCustomer);

        String email = resolver.emailForCustomer(4L);

        assertNull(email);
    }
}