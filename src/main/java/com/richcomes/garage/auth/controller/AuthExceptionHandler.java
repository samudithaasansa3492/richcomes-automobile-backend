package com.richcomes.garage.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> invalidCredentials(AuthenticationException exception) {
        return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .body(Map.of("code", "UNAUTHORIZED", "message", "Invalid username or password"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("code", "INVALID_REQUEST", "message", "Invalid login request"));
    }
}
