
package com.richcomes.garage.appointment.service;

import com.richcomes.garage.appointment.entity.Appointment;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.repository.AppointmentRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.vehicle.service.VehicleService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class AppointmentStaffCancellationSecurityTest {

    private static final Long APPOINTMENT_ID = 105L;
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
    void ownerCanCancelPendingAppointment() {

        Appointment appointment = appointment(
                AppointmentStatus.PENDING
        );

        mockAppointmentSave(appointment);

        var response = appointmentService.cancelStaffAppointment(
                APPOINTMENT_ID,
                "  Customer requested cancellation  ",
                staffPrincipal("OWNER", true)
        );

        assertEquals(AppointmentStatus.CANCELLED, response.getStatus());
        assertEquals(
                "Customer requested cancellation",
                response.getCancellationReason()
        );
        assertEquals(ACCOUNT_ID, response.getCancelledByAccountId());
        assertEquals("STAFF", response.getCancelledByType());
        assertNotNull(response.getCancelledAt());

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void authorizedCashierCanCancelApprovedAppointment() {

        Appointment appointment = appointment(
                AppointmentStatus.APPROVED
        );

        mockAppointmentSave(appointment);

        var response = appointmentService.cancelStaffAppointment(
                APPOINTMENT_ID,
                "Customer cannot attend",
                staffPrincipal("CASHIER", true)
        );

        assertEquals(AppointmentStatus.CANCELLED, response.getStatus());
        assertEquals("STAFF", response.getCancelledByType());

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void cashierWithoutPermissionCannotCancel() {

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        staffPrincipal("CASHIER", false)
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void ownerWithoutPermissionCannotCancel() {

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        staffPrincipal("OWNER", false)
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void customerCannotUseStaffCancellation() {

        AccountPrincipal principal = new AccountPrincipal(
                ACCOUNT_ID,
                null,
                "customer",
                Set.of("CUSTOMER"),
                Set.of("appointment:manage"),
                10L
        );

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        principal
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void unauthenticatedUserCannotCancel() {

        assertError(
                HttpStatus.FORBIDDEN,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        null
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = AppointmentStatus.class,
            names = {
                    "REJECTED",
                    "DONE",
                    "NOT_COMPLETED",
                    "CANCELLED"
            }
    )
    void staffCannotCancelInvalidStatus(
            AppointmentStatus status) {

        Appointment appointment = appointment(status);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        assertError(
                HttpStatus.CONFLICT,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        staffPrincipal("OWNER", true)
                )
        );

        assertEquals(status, appointment.getStatus());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void blankCancellationReasonIsRejected() {

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "   ",
                        staffPrincipal("OWNER", true)
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void missingCancellationReasonIsRejected() {

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        null,
                        staffPrincipal("OWNER", true)
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void cancellationReasonOver1000CharactersIsRejected() {

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "A".repeat(1001),
                        staffPrincipal("OWNER", true)
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void invalidAppointmentIdIsRejected() {

        assertError(
                HttpStatus.BAD_REQUEST,
                () -> appointmentService.cancelStaffAppointment(
                        -1L,
                        "Cancel appointment",
                        staffPrincipal("OWNER", true)
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void nonexistentAppointmentReturnsNotFound() {

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.empty());

        assertError(
                HttpStatus.NOT_FOUND,
                () -> appointmentService.cancelStaffAppointment(
                        APPOINTMENT_ID,
                        "Cancel appointment",
                        staffPrincipal("OWNER", true)
                )
        );

        verify(appointmentRepository, never()).save(any());
    }

    private void mockAppointmentSave(Appointment appointment) {

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        when(appointmentRepository.save(appointment))
                .thenReturn(appointment);
    }

    private Appointment appointment(AppointmentStatus status) {

        Appointment appointment = new Appointment();

        ReflectionTestUtils.setField(
                appointment,
                "id",
                APPOINTMENT_ID
        );

        appointment.setCustomerName("Nimal Perera");
        appointment.setCustomerMobile("0771234567");
        appointment.setVehicleNumber("ABC-2477");
        appointment.setRequestedDate(LocalDate.of(2026, 12, 1));
        appointment.setStatus(status);

        return appointment;
    }

    private AccountPrincipal staffPrincipal(
            String role,
            boolean canManage) {

        return new AccountPrincipal(
                ACCOUNT_ID,
                null,
                "staff",
                Set.of(role),
                canManage
                        ? Set.of("appointment:manage")
                        : Set.of(),
                null
        );
    }

    private void assertError(
            HttpStatus status,
            org.junit.jupiter.api.function.Executable action) {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                action
        );

        assertEquals(status, exception.getStatusCode());
    }
}
