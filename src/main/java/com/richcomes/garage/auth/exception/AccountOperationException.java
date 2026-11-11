package com.richcomes.garage.auth.exception;

/** Domain failure mapped to a safe API response by the account exception handler. */
public class AccountOperationException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        EMPLOYEE_NOT_FOUND,
        ACCOUNT_NOT_FOUND,
        USERNAME_ALREADY_EXISTS,
        EMPLOYEE_ALREADY_HAS_ACCOUNT,
        UNKNOWN_ROLE,
        EMPLOYEE_INACTIVE,
        ACCOUNT_HAS_NO_ROLES,
        ACCOUNT_CONFLICT,
        SELF_MODIFICATION,
        LAST_ACTIVE_ADMIN,
        ADMIN_ROLE_NOT_CONFIGURED
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
