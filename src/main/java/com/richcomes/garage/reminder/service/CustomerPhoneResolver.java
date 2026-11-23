package com.richcomes.garage.reminder.service;

/**
 * Interface for resolving customer contact information with consent/verification checks.
 *
 * Contract Requirements (Member 02):
 * - Customer entity must have: mobile (validated Sri Lankan format), email
 * - Customer entity SHOULD have (for production):
 *   - boolean smsConsent (default false)
 *   - boolean emailConsent (default false)
 *   - boolean mobileVerified (default false)
 *   - boolean emailVerified (default false)
 * - Resolver fails closed when consent/verification unavailable
 * - Never returns unverified or non-consenting contacts
 *
 * Implementation: CustomerPhoneResolverImpl (uses Member 02's CustomerService)
 */
public interface CustomerPhoneResolver {

    /**
     * Resolve verified SMS phone number for customer.
     * Returns null if: customer not found, inactive, no mobile, invalid format,
     * mobile not verified, or smsConsent=false.
     */
    String phoneForCustomer(Long customerId);

    /**
     * Resolve verified email for customer.
     * Returns null if: customer not found, inactive, no email, invalid format,
     * email not verified, or emailConsent=false.
     */
    String emailForCustomer(Long customerId);
}
