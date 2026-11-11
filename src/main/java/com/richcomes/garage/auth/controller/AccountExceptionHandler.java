package com.richcomes.garage.auth.controller;

import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.employee.controller.EmployeeController;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {AccountController.class, EmployeeController.class})
public class AccountExceptionHandler {
    @ExceptionHandler(AccountOperationException.class)
    public ResponseEntity<Map<String, String>> accountFailure(AccountOperationException exception) {
        int status = switch (exception.getCode()) {
            case INVALID_REQUEST, UNKNOWN_ROLE -> 400;
            case EMPLOYEE_NOT_FOUND, ACCOUNT_NOT_FOUND -> 404;
            case SELF_MODIFICATION -> 403;
            case ADMIN_ROLE_NOT_CONFIGURED -> 503;
            default -> 409;
        };
        return response(status, exception.getCode().name(), exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return response(400, "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseFailure(Exception exception) {
        return response(503, "ACCOUNT_OPERATION_UNAVAILABLE", "Operation unavailable; retry later");
    }

    private ResponseEntity<Map<String, String>> response(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("code", code, "message", message));
    }
}
