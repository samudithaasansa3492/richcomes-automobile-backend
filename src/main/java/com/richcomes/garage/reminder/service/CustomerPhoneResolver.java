package com.richcomes.garage.reminder.service;

/** Implement as a Spring @Component using your existing CustomerService.
 * Return the customer's verified SMS number, not a caller-supplied number.
 */
public interface CustomerPhoneResolver {
    String phoneForCustomer(Long customerId);
}
