package com.richcomes.provisioning;

/** Only fixed, non-sensitive messages are exposed by the command. */
public final class ProvisioningException extends RuntimeException {
    public enum Code {
        INVALID_INPUT, SEED_REQUIRED, ADMIN_ALREADY_EXISTS, EMPLOYEE_NOT_FOUND,
        EMPLOYEE_INACTIVE, EMPLOYEE_HAS_ACCOUNT, USERNAME_EXISTS, DATABASE_FAILURE
    }

    private final Code code;

    public ProvisioningException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
}
