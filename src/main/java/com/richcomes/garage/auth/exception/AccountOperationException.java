package com.richcomes.garage.auth.exception;

/** Domain failure mapped to a safe API response by the account exception handler. */
public class AccountOperationException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        EMPLOYEE_NOT_FOUND,
        CUSTOMER_NOT_FOUND,
        CUSTOMER_ACCOUNT_NOT_FOUND,
        CUSTOMER_ALREADY_HAS_ACCOUNT,
        CUSTOMER_INACTIVE,
        CUSTOMER_ROLE_NOT_CONFIGURED,
        CASHIER_NOT_ELIGIBLE,
        CUSTOMER_REGISTRATION_NOT_ALLOWED,
        CUSTOMER_VERIFICATION_REQUIRED,
        CUSTOMER_PASSWORD_NOT_SET,
        CUSTOMER_ONBOARDING_UNAVAILABLE,
        PERMISSION_DELEGATION_FORBIDDEN,
        ACCOUNT_NOT_FOUND,
        USERNAME_ALREADY_EXISTS,
        EMPLOYEE_ALREADY_HAS_ACCOUNT,
        UNKNOWN_ROLE,
        EMPLOYEE_INACTIVE,
        ACCOUNT_HAS_NO_ROLES,
        ACCOUNT_CONFLICT,
        SELF_MODIFICATION,
        TECHNICAL_ACCOUNT_PROTECTED,
        LAST_ACTIVE_OWNER,
        OWNER_ROLE_NOT_CONFIGURED
    }

    private final Code code;

    public AccountOperationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
