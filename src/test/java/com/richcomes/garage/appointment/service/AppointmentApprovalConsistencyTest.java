
package com.richcomes.garage.appointment.service;

import com.richcomes.garage.appointment.entity.Appointment;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.repository.AppointmentRepository;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.vehicle.entity.Vehicle;
import com.richcomes.garage.vehicle.enums.VehicleStatus;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentApprovalConsistencyTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private AppointmentService appointmentService;

    private static final Long APPOINTMENT_ID = 105L;

    private LocalDate futureDate() {
        return LocalDate.now(ZoneId.of("Asia/Colombo")).plusDays(2);
    }

    @Test
    void shouldApproveWhenCustomerAndVehicleAreActive() {

        Appointment appointment = registeredAppointment(
                CustomerStatus.ACTIVE,
                VehicleStatus.ACTIVE,
                true
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = appointmentService.approveAppointment(
                APPOINTMENT_ID,
                futureDate(),
                LocalTime.of(9, 0),
                "Confirmed with customer"
        );

        assertEquals(AppointmentStatus.APPROVED, response.getStatus());
        assertEquals(futureDate(), response.getConfirmedDate());
        assertEquals(LocalTime.of(9, 0), response.getAppointmentTime());
        assertNotNull(response.getContactedAt());

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void shouldRejectApprovalForSuspendedCustomer() {

        Appointment appointment = registeredAppointment(
                CustomerStatus.SUSPENDED,
                VehicleStatus.ACTIVE,
                true
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> appointmentService.approveAppointment(
                        APPOINTMENT_ID,
                        futureDate(),
                        LocalTime.of(10, 0),
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(AppointmentStatus.PENDING, appointment.getStatus());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectApprovalForInactiveVehicle() {

        Appointment appointment = registeredAppointment(
                CustomerStatus.ACTIVE,
                VehicleStatus.INACTIVE,
                true
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> appointmentService.approveAppointment(
                        APPOINTMENT_ID,
                        futureDate(),
                        LocalTime.of(10, 0),
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(AppointmentStatus.PENDING, appointment.getStatus());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectVehicleOwnershipMismatch() {

        Appointment appointment = registeredAppointment(
                CustomerStatus.ACTIVE,
                VehicleStatus.ACTIVE,
                false
        );

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> appointmentService.approveAppointment(
                        APPOINTMENT_ID,
                        futureDate(),
                        LocalTime.of(11, 0),
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(AppointmentStatus.PENDING, appointment.getStatus());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectIncompleteRegisteredAppointment() {

        Appointment appointment = registeredAppointment(
                CustomerStatus.ACTIVE,
                VehicleStatus.ACTIVE,
                true
        );

        appointment.setVehicle(null);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> appointmentService.approveAppointment(
                        APPOINTMENT_ID,
                        futureDate(),
                        LocalTime.of(12, 0),
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void shouldAllowGuestAppointmentApproval() {

        Appointment appointment = new Appointment();

        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setCustomerName("Guest Customer");
        appointment.setCustomerMobile("0771234567");
        appointment.setVehicleNumber("ABC-2477");
        appointment.setRequestedDate(futureDate());

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));

        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = appointmentService.approveAppointment(
                APPOINTMENT_ID,
                futureDate(),
                LocalTime.of(14, 0),
                "Guest booking confirmed"
        );

        assertEquals(AppointmentStatus.APPROVED, response.getStatus());

        assertNull(response.getCustomerId());
        assertNull(response.getVehicleId());

        verify(appointmentRepository).save(appointment);
    }

    private Appointment registeredAppointment(
            CustomerStatus customerStatus,
            VehicleStatus vehicleStatus,
            boolean correctOwner) {

        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 10L);
        customer.setStatus(customerStatus);

        Vehicle vehicle = new Vehicle();
        ReflectionTestUtils.setField(vehicle, "id", 20L);
        vehicle.setStatus(vehicleStatus);
        vehicle.setVehicleNumber("ABC-2477");

        if (correctOwner) {
            vehicle.setCustomer(customer);
        } else {
            Customer anotherCustomer = new Customer();
            ReflectionTestUtils.setField(anotherCustomer, "id", 99L);
            vehicle.setCustomer(anotherCustomer);
        }

        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setCustomerName("Nimal Perera");
        appointment.setCustomerMobile("0771234567");
        appointment.setVehicleNumber("ABC-2477");
        appointment.setRequestedDate(futureDate());

        return appointment;
    }
}
