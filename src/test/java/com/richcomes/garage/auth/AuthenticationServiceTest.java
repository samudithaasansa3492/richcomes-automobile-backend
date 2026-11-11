package com.richcomes.garage.auth;

import com.richcomes.garage.auth.dto.LoginRequest;
import com.richcomes.garage.auth.dto.LoginResponse;
import com.richcomes.garage.auth.entity.UserAccount;
import com.richcomes.garage.auth.repository.UserAccountRepository;
import com.richcomes.garage.auth.service.AccessTokenService;
import com.richcomes.garage.auth.service.AuthenticationService;
import com.richcomes.garage.employee.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class AuthenticationServiceTest {
    private UserAccountRepository accounts;
    private PasswordEncoder encoder;
    private AccessTokenService tokens;
    private AuthenticationService service;
    private UserAccount account;
    private Employee employee;
    private final LoginRequest request = new LoginRequest(" CASHIER.ONE ", "correct horse battery staple");

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        encoder = mock(PasswordEncoder.class);
        tokens = mock(AccessTokenService.class);
        when(encoder.encode(anyString())).thenReturn("dummy-hash");
        service = new AuthenticationService(accounts, encoder, tokens);
        employee = new Employee();
        account = new UserAccount(employee, "cashier.one", "stored-hash");
        ReflectionTestUtils.setField(account, "id", 42L);
        account.enable();
        when(accounts.findByUsername("cashier.one")).thenReturn(Optional.of(account));
        when(encoder.matches(request.password(), "stored-hash")).thenReturn(true);
    }

    @Test
    void successfulLoginIssuesTokenForAccountId() {
        var expected = new LoginResponse("token", "Bearer", 900);
        when(tokens.issue(42L)).thenReturn(expected);
        assertEquals(expected, service.login(request));
        verify(accounts).findByUsername("cashier.one");
    }

    @Test
    void wrongPasswordDoesNotIssueToken() {
        when(encoder.matches(request.password(), "stored-hash")).thenReturn(false);
        denied(request);
    }

    @Test
    void unknownUsernameStillPerformsPasswordWork() {
        when(accounts.findByUsername("cashier.one")).thenReturn(Optional.empty());
        denied(request);
        verify(encoder).matches(request.password(), "dummy-hash");
    }

    @Test
    void disabledAccountReturnsSameGenericError() {
        account.disable();
        denied(request);
    }

    @Test
    void inactiveEmployeeReturnsSameGenericError() {
        employee.setActive(false);
        denied(request);
    }

    @Test
    void invalidUsernameUsesDummyHash() {
        denied(new LoginRequest("invalid name", request.password()));
        verify(encoder).matches(request.password(), "dummy-hash");
    }

    @Test
    void rejectsOversizedUnicodePasswordBeforeBcrypt() {
        denied(new LoginRequest("cashier.one", "ආ".repeat(25)));
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void unusableStoredHashDoesNotExposeDetails() {
        when(encoder.matches(request.password(), "stored-hash")).thenThrow(new IllegalArgumentException("private detail"));
        denied(request);
    }

    @Test
    void requestLoggingRedactsPassword() {
        assertFalse(request.toString().contains(request.password()));
    }

    private void denied(LoginRequest request) {
        var failure = assertThrows(BadCredentialsException.class, () -> service.login(request));
        assertEquals("Invalid username or password", failure.getMessage());
        verifyNoInteractions(tokens);
    }
}
