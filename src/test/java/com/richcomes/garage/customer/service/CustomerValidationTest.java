
package com.richcomes.garage.customer.service;

import com.richcomes.garage.customer.dto.CustomerCreateRequest;
import com.richcomes.garage.customer.dto.CustomerUpdateRequest;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.enums.CustomerType;
import com.richcomes.garage.customer.repository.CustomerRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @BeforeAll
    static void setUpValidation() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidation() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidPersonalCustomerDetails() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setOfficeNumber("0112345678");
        request.setCreditLimit(new BigDecimal("10000.50"));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsInvalidMobileNumber() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setMobile("0112345678");

        assertViolation(request, "mobile");
    }

    @Test
    void rejectsInvalidNicFormat() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setNic("12345");

        assertViolation(request, "nic");
    }

    @Test
    void rejectsInvalidOfficeNumber() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setOfficeNumber("1234567890");

        assertViolation(request, "officeNumber");
    }

    @Test
    void acceptsMobileOrLandlineAsOfficeNumber() {

        CustomerCreateRequest request = validPersonalRequest();

        request.setOfficeNumber("0771234567");
        assertTrue(validator.validate(request).isEmpty());

        request.setOfficeNumber("0112345678");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsInvalidEmail() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setEmail("not-an-email");

        assertViolation(request, "email");
    }

    @Test
    void rejectsNegativeCreditLimit() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setCreditLimit(new BigDecimal("-1.00"));

        assertViolation(request, "creditLimit");
    }

    @Test
    void rejectsCreditLimitWithMoreThanTwoDecimalPlaces() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setCreditLimit(new BigDecimal("100.123"));

        assertViolation(request, "creditLimit");
    }

    @Test
    void rejectsMissingRequiredFields() {

        CustomerCreateRequest request = validPersonalRequest();

        request.setName(" ");
        request.setAddress(null);
        request.setCity("");

        assertViolation(request, "name");
        assertViolation(request, "address");
        assertViolation(request, "city");
    }

    @Test
    void rejectsInvalidUpdateRequestFields() {

        CustomerUpdateRequest request = new CustomerUpdateRequest();

        request.setMobile("0112345678");
        request.setEmail("invalid-email");
        request.setCreditLimit(new BigDecimal("-10.00"));

        assertViolation(request, "mobile");
        assertViolation(request, "email");
        assertViolation(request, "creditLimit");
    }

    @Test
    void rejectsDuplicateNic() {

        CustomerCreateRequest request = validPersonalRequest();

        when(customerRepository.existsByNic("123456789V"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerService.createCustomer(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(customerRepository, never())
                .save(any(Customer.class));
    }

    @Test
    void requiresNicForPersonalCustomer() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setNic(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerService.createCustomer(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(customerRepository, never())
                .save(any(Customer.class));
    }

    @Test
    void normalizesNicAndOptionalFieldsWhenCreatingCustomer() {

        CustomerCreateRequest request = validPersonalRequest();

        request.setNic("123456789v");
        request.setName("  Nimal Perera  ");
        request.setOfficeNumber("");
        request.setEmail("");

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = customerService.createCustomer(request);

        assertEquals("123456789V", response.getNic());
        assertEquals("Nimal Perera", response.getName());
        assertNull(response.getOfficeNumber());
        assertNull(response.getEmail());
        assertEquals(CustomerStatus.ACTIVE, response.getStatus());

        verify(customerRepository)
                .existsByNic("123456789V");
    }

    @Test
    void companyCustomerDoesNotStoreNic() {

        CustomerCreateRequest request = validPersonalRequest();
        request.setCustomerType(CustomerType.COMPANY);

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = customerService.createCustomer(request);

        assertEquals(
                CustomerType.COMPANY,
                response.getCustomerType()
        );

        assertNull(response.getNic());

        verify(customerRepository, never())
                .existsByNic(any());
    }

    @Test
    void suspendsCustomer() {

        Customer customer = existingCustomer(CustomerStatus.ACTIVE);

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        when(customerRepository.save(customer))
                .thenReturn(customer);

        var response = customerService.suspendCustomer(10L);

        assertEquals(
                CustomerStatus.SUSPENDED,
                response.getStatus()
        );

        verify(customerRepository).save(customer);
    }

    @Test
    void reactivatesCustomer() {

        Customer customer = existingCustomer(CustomerStatus.SUSPENDED);

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        when(customerRepository.save(customer))
                .thenReturn(customer);

        var response = customerService.activateCustomer(10L);

        assertEquals(
                CustomerStatus.ACTIVE,
                response.getStatus()
        );

        verify(customerRepository).save(customer);
    }

    @Test
    void trimsUpdatedNameAndClearsBlankOptionalOfficeNumber() {

        Customer customer = existingCustomer(CustomerStatus.ACTIVE);

        CustomerUpdateRequest request = new CustomerUpdateRequest();
        request.setName("  Updated Name  ");
        request.setOfficeNumber("");

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        when(customerRepository.save(customer))
                .thenReturn(customer);

        var response = customerService.updateCustomer(10L, request);

        assertEquals("Updated Name", response.getName());
        assertNull(response.getOfficeNumber());

        verify(customerRepository).save(customer);
    }

    private static void assertViolation(
            Object request,
            String property) {

        assertTrue(
                validator.validate(request)
                        .stream()
                        .anyMatch(v ->
                                property.equals(
                                        v.getPropertyPath().toString()
                                )
                        ),
                "Expected validation error for: " + property
        );
    }

    private static CustomerCreateRequest validPersonalRequest() {

        CustomerCreateRequest request = new CustomerCreateRequest();

        request.setCustomerType(CustomerType.PERSONAL);
        request.setName("Nimal Perera");
        request.setNic("123456789V");
        request.setAddress("10 Main Street");
        request.setMobile("0771234567");
        request.setCity("Colombo");
        request.setEmail("nimal@example.com");

        return request;
    }

    private static Customer existingCustomer(
            CustomerStatus status) {

        Customer customer = new Customer();

        ReflectionTestUtils.setField(
                customer,
                "id",
                10L
        );

        customer.setCustomerType(CustomerType.PERSONAL);
        customer.setName("Nimal Perera");
        customer.setNic("123456789V");
        customer.setAddress("10 Main Street");
        customer.setMobile("0771234567");
        customer.setCity("Colombo");
        customer.setStatus(status);

        return customer;
    }
}
