
package com.richcomes.garage.appointment.controller;

import com.richcomes.garage.appointment.dto.AppointmentResponse;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.service.AppointmentService;
import com.richcomes.garage.auth.config.SecurityConfig;
import com.richcomes.garage.auth.security.AccountJwtAuthenticationConverter;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.auth.security.SecurityErrorHandlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(AppointmentCancellationHttpSecurityTest.TestConfig.class)
class AppointmentCancellationHttpSecurityTest {

    private static final Long APPOINTMENT_ID = 105L;

    private static final String REASON_JSON =
            "{\"reason\":\"Customer requested cancellation\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AccountJwtAuthenticationConverter converter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {

        reset(appointmentService, jwtDecoder, converter);

        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedUserCannotCancelStaffAppointment()
            throws Exception {

        cancelStaff(null)
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void unauthenticatedUserCannotCancelCustomerAppointment()
            throws Exception {

        cancelCustomer(null)
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void cashierWithReadOnlyPermissionCannotCancelStaffAppointment()
            throws Exception {

        authenticate(
                "cashier-read",
                "CASHIER",
                Set.of("appointment:read"),
                null
        );

        cancelStaff("cashier-read")
                .andExpect(status().isForbidden());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void customerCannotUseStaffCancellationEndpoint()
            throws Exception {

        authenticate(
                "customer-staff",
                "CUSTOMER",
                Set.of("customer:self"),
                10L
        );

        cancelStaff("customer-staff")
                .andExpect(status().isForbidden());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void ownerWithPermissionCanUseStaffCancellationEndpoint()
            throws Exception {

        authenticate(
                "owner-token",
                "OWNER",
                Set.of("appointment:manage"),
                null
        );

        when(appointmentService.cancelStaffAppointment(
                eq(APPOINTMENT_ID),
                eq("Customer requested cancellation"),
                any(AccountPrincipal.class)
        )).thenReturn(cancelledResponse());

        cancelStaff("owner-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(appointmentService).cancelStaffAppointment(
                eq(APPOINTMENT_ID),
                eq("Customer requested cancellation"),
                argThat(p -> p.roles().contains("OWNER"))
        );
    }

    @Test
    void authorizedCashierCanUseStaffCancellationEndpoint()
            throws Exception {

        authenticate(
                "cashier-manage",
                "CASHIER",
                Set.of("appointment:manage"),
                null
        );

        when(appointmentService.cancelStaffAppointment(
                eq(APPOINTMENT_ID),
                anyString(),
                any(AccountPrincipal.class)
        )).thenReturn(cancelledResponse());

        cancelStaff("cashier-manage")
                .andExpect(status().isOk());

        verify(appointmentService).cancelStaffAppointment(
                eq(APPOINTMENT_ID),
                eq("Customer requested cancellation"),
                argThat(p -> p.roles().contains("CASHIER"))
        );
    }

    @Test
    void authorizedCustomerCanUseCustomerCancellationEndpoint()
            throws Exception {

        authenticate(
                "customer-own",
                "CUSTOMER",
                Set.of("customer:self"),
                10L
        );

        when(appointmentService.cancelCustomerAppointment(
                eq(APPOINTMENT_ID),
                anyString(),
                any(AccountPrincipal.class)
        )).thenReturn(cancelledResponse());

        cancelCustomer("customer-own")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(105))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(appointmentService).cancelCustomerAppointment(
                eq(APPOINTMENT_ID),
                eq("Customer requested cancellation"),
                argThat(p -> Long.valueOf(10L).equals(p.customerId()))
        );
    }

    @Test
    void cashierCannotUseCustomerOnlyCancellationEndpoint()
            throws Exception {

        authenticate(
                "cashier-customer",
                "CASHIER",
                Set.of("appointment:manage"),
                null
        );

        cancelCustomer("cashier-customer")
                .andExpect(status().isForbidden());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void customerWithoutSelfPermissionCannotCancel()
            throws Exception {

        authenticate(
                "customer-no-permission",
                "CUSTOMER",
                Set.of(),
                10L
        );

        cancelCustomer("customer-no-permission")
                .andExpect(status().isForbidden());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void customerCannotReadStaffAppointmentDetails()
            throws Exception {

        authenticate(
                "customer-read",
                "CUSTOMER",
                Set.of("customer:self"),
                10L
        );

        mvc.perform(
                get("/api/v1/appointments/105")
                        .header(
                                "Authorization",
                                "Bearer customer-read"
                        )
        ).andExpect(status().isForbidden());

        verifyNoInteractions(appointmentService);
    }

    private void authenticate(
            String token,
            String role,
            Set<String> permissions,
            Long customerId) {

        Long accountId = 50L;

        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(accountId.toString())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3600))
                .build();

        AccountPrincipal principal = new AccountPrincipal(
                accountId,
                null,
                role.toLowerCase(),
                Set.of(role),
                permissions,
                customerId
        );

        var authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(converter.convert(jwt))
                .thenReturn(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal,
                                null,
                                authorities
                        )
                );
    }

    private ResultActions cancelStaff(String token)
            throws Exception {

        MockHttpServletRequestBuilder request = patch(
                "/api/v1/appointments/{id}/cancel",
                APPOINTMENT_ID
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON);

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        return mvc.perform(request);
    }

    private ResultActions cancelCustomer(String token)
            throws Exception {

        MockHttpServletRequestBuilder request = patch(
                "/api/customer/appointments/{id}/cancel",
                APPOINTMENT_ID
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON);

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        return mvc.perform(request);
    }

    private AppointmentResponse cancelledResponse() {

        AppointmentResponse response = new AppointmentResponse();

        response.setId(APPOINTMENT_ID);
        response.setStatus(AppointmentStatus.CANCELLED);
        response.setCancellationReason(
                "Customer requested cancellation"
        );

        return response;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            SecurityErrorHandlers.class,
            AppointmentController.class,
            CustomerAppointmentController.class
    })
    static class TestConfig {

        @Bean
        AppointmentService appointmentService() {
            return mock(AppointmentService.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        AccountJwtAuthenticationConverter converter() {
            return mock(AccountJwtAuthenticationConverter.class);
        }
    }
}
