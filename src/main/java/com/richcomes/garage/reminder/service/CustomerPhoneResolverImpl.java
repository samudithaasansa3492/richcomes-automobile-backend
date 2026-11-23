package com.richcomes.garage.reminder.service;

import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Implementation of CustomerPhoneResolver using Member 02's CustomerService.
 *
 * This resolver returns the mobile number only if:
 * - Customer exists and is ACTIVE
 * - Mobile number is present and in valid Sri Lankan format
 * - Mobile number has been verified (mobileVerified == true)
 * - Customer has given SMS consent (smsConsent == true)
 *
 * Otherwise returns null (fail-closed).
 */
@Component
public class CustomerPhoneResolverImpl implements CustomerPhoneResolver {

    private static final Logger log = LoggerFactory.getLogger(CustomerPhoneResolverImpl.class);

    // Sri Lankan mobile format: +947XXXXXXXX or 07XXXXXXXX (10 digits after 0 or 94)
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^(?:\\+94|0)7[0-9]{8}$");

    // Email pattern for validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final CustomerService customerService;

    public CustomerPhoneResolverImpl(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public String phoneForCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            log.warn("Invalid customer ID: {}", customerId);
            return null;
        }

        try {
            Customer customer = customerService.getCustomerEntityById(customerId);

            if (customer == null) {
                log.warn("Customer not found for ID: {}", customerId);
                return null;
            }

            // Check if customer is active
            if (customer.getStatus() != com.richcomes.garage.customer.enums.CustomerStatus.ACTIVE) {
                log.warn("Customer {} is not active (status: {})", customerId, customer.getStatus());
                return null;
            }

            String mobile = customer.getMobile();

            if (mobile == null || mobile.isBlank()) {
                log.warn("Customer {} has no mobile number", customerId);
                return null;
            }

            // Validate format - Sri Lankan mobile numbers
            if (!MOBILE_PATTERN.matcher(mobile.trim()).matches()) {
                log.warn("Customer {} mobile number format invalid: {}", customerId, maskPhone(mobile));
                return null;
            }

            // Check mobile verification
            if (!customer.isMobileVerified()) {
                log.warn("Customer {} mobile number not verified", customerId);
                return null;
            }

            // Check SMS consent
            if (!customer.isSmsConsent()) {
                log.warn("Customer {} has not consented to SMS notifications", customerId);
                return null;
            }

            log.debug("Resolved phone for customer {}: {}", customerId, maskPhone(mobile));
            return mobile.trim();

        } catch (Exception e) {
            log.error("Error resolving phone for customer {}: {}", customerId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Resolve email for customer (for future email notifications).
     * Returns null if email missing, invalid format, not verified, or no consent.
     */
    public String emailForCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return null;
        }

        try {
            Customer customer = customerService.getCustomerEntityById(customerId);

            if (customer == null || customer.getStatus() != com.richcomes.garage.customer.enums.CustomerStatus.ACTIVE) {
                return null;
            }

            String email = customer.getEmail();

            if (email == null || email.isBlank()) {
                return null;
            }

            if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
                return null;
            }

            // Check email verification
            if (!customer.isEmailVerified()) {
                log.warn("Customer {} email not verified", customerId);
                return null;
            }

            // Check email consent
            if (!customer.isEmailConsent()) {
                log.warn("Customer {} has not consented to email notifications", customerId);
                return null;
            }

            return email.trim();

        } catch (Exception e) {
            log.error("Error resolving email for customer {}: {}", customerId, e.getMessage(), e);
            return null;
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 1);
    }
}