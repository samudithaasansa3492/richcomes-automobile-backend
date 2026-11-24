
package com.richcomes.garage.appointment.service;

import com.richcomes.garage.appointment.entity.Appointment;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.repository.AppointmentRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.vehicle.service.VehicleService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentCustomerCancellationSecurityTest {

    private static final Long APPOINTMENT_ID = 105L;
    private static final Long CUSTOMER_ID = 10L;
    private static final Long ACCOUNT_ID = 50L;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void shouldAllowCustomerToCancelOwnPendingAppointment() {

        Appointment appointment = appointment(
                CUSTOMER_ID,
                AppointmentStatus.PENDING
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        when(appointmentRepository.save(appointment))
                .thenReturn(appointment);

        var response = appointmentService.cancelCustomerAppointment(
                APPOINTMENT_ID,
                "  Customer requested cancellation  ",
                customerPrincipal()
        );

        assertEquals(
                AppointmentStatus.CANCELLED,
                response.getStatus()
        );

        assertEquals(
                "Customer requested cancellation",
                response.getCancellationReason()
        );

        assertEquals(
                ACCOUNT_ID,
                response.getCancelledByAccountId()
        );

        assertEquals(
                "CUSTOMER",
                response.getCancelledByType()
        );

        assertNotNull(response.getCancelledAt());

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void shouldRejectCancellationOfAnotherCustomersAppointment() {

        Appointment appointment = appointment(
                99L,
                AppointmentStatus.PENDING
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        assertError(
                HttpStatus.NOT_FOUND,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        customerPrincipal()
                )
        );

        assertEquals(
                AppointmentStatus.PENDING,
                appointment.getStatus()
        );

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCancellationOfUnlinkedGuestAppointment() {

        Appointment appointment = appointment(
                null,
                AppointmentStatus.PENDING
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        assertError(
                HttpStatus.NOT_FOUND,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel guest booking",
                        customerPrincipal()
                )
        );

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCustomerCancellationOfApprovedAppointment() {

        assertInvalidStatus(AppointmentStatus.APPROVED);
    }

    @Test
    void shouldRejectCancellationOfAlreadyCancelledAppointment() {

        assertInvalidStatus(AppointmentStatus.CANCELLED);
    }

    @Test
    void shouldRejectCancellationWithoutAuthentication() {

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        null
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldRejectPrincipalWithoutCustomerId() {

        AccountPrincipal principal = new AccountPrincipal(
                ACCOUNT_ID,
                null,
                "customer",
                Set.of("CUSTOMER"),
                Set.of("customer:self"),
                null
        );

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        principal
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldRejectCustomerWithoutSelfPermission() {

        AccountPrincipal principal = new AccountPrincipal(
                ACCOUNT_ID,
                null,
                "customer",
                Set.of("CUSTOMER"),
                Set.of(),
                CUSTOMER_ID
        );

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        principal
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldRejectNonCustomerRole() {

        AccountPrincipal principal = new AccountPrincipal(
                ACCOUNT_ID,
                null,
                "staff",
                Set.of("CASHIER"),
                Set.of("customer:self"),
                CUSTOMER_ID
        );

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        principal
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldRejectBlankCancellationReason() {

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "   ",
                        customerPrincipal()
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldRejectCancellationReasonOver1000Characters() {

        String reason = "A".repeat(1001);

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        reason,
                        customerPrincipal()
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldRejectInvalidAppointmentId() {

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelCustomerAppointment(
                        -1L,
                        "Cancel appointment",
                        customerPrincipal()
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldReturnNotFoundWhenAppointmentDoesNotExist() {

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.empty());

        assertError(
                HttpStatus.NOT_FOUND,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        customerPrincipal()
                )
        );

        verify(appointmentRepository, never()).save(any());
    }

    private void assertInvalidStatus(AppointmentStatus status) {

        Appointment appointment = appointment(
                CUSTOMER_ID,
                status
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        assertError(
                HttpStatus.CONFLICT,
                () -> appointmentService.cancelCustomerAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        customerPrincipal()
                )
        );

        assertEquals(status, appointment.getStatus());

        verify(appointmentRepository, never()).save(any());
    }

    private void assertError(
            HttpStatus expectedStatus,
            org.junit.jupiter.api.function.Executable action) {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                action
        );

        assertEquals(
                expectedStatus,
                exception.getStatusCode()
        );
    }

    private AccountPrincipal customerPrincipal() {

        return new AccountPrincipal(
                ACCOUNT_ID,
                null,
                "nimal101",
                Set.of("CUSTOMER"),
                Set.of("customer:self"),
                CUSTOMER_ID
        );
    }

    private Appointment appointment(
            Long customerId,
            AppointmentStatus status) {

        Appointment appointment = new Appointment();

        ReflectionTestUtils.setField(
                appointment,
                "id",
                APPOINTMENT_ID
        );

        if (customerId != null) {

            Customer customer = new Customer();

            ReflectionTestUtils.setField(
                    customer,
                    "id",
                    customerId
            );

            appointment.setCustomer(customer);
        }

        appointment.setCustomerName("Nimal Perera");
        appointment.setCustomerMobile("0771234567");
        appointment.setVehicleNumber("ABC-2477");
        appointment.setRequestedDate(LocalDate.of(2026, 12, 1));
        appointment.setStatus(status);

        return appointment;
    }
}
