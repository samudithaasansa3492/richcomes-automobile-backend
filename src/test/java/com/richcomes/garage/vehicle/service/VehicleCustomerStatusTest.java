
package com.richcomes.garage.vehicle.service;

import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.vehicle.dto.VehicleCreateRequest;
import com.richcomes.garage.vehicle.entity.Vehicle;
import com.richcomes.garage.vehicle.enums.VehicleStatus;
import com.richcomes.garage.vehicle.repository.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleCustomerStatusTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private VehicleService vehicleService;

    private static final Long CUSTOMER_ID = 10L;
    private static final Long VEHICLE_ID = 20L;

    @Test
    void shouldRegisterVehicleForActiveCustomer() {

        Customer customer = createCustomer(CustomerStatus.ACTIVE);

        VehicleCreateRequest request = validCreateRequest();

        request.setVehicleNumber(" abc-2477 ");

        when(customerService.getCustomerEntityById(CUSTOMER_ID))
                .thenReturn(customer);

        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(invocation -> {
                    Vehicle vehicle = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            vehicle,
                            "id",
                            VEHICLE_ID
                    );
                    return vehicle;
                });

        var response = vehicleService.createVehicle(request);

        assertNotNull(response);
        assertEquals(VEHICLE_ID, response.getId());
        assertEquals(CUSTOMER_ID, response.getCustomerId());
        assertEquals("ABC-2477", response.getVehicleNumber());
        assertEquals(VehicleStatus.ACTIVE, response.getStatus());

        assertEquals(
                Double.valueOf(1256.2),
                response.getCurrentMileage()
        );

        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void shouldRejectRegistrationForSuspendedCustomer() {

        Customer customer = createCustomer(CustomerStatus.SUSPENDED);

        when(customerService.getCustomerEntityById(CUSTOMER_ID))
                .thenReturn(customer);

        VehicleCreateRequest request = validCreateRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldActivateVehicleForActiveCustomer() {

        Customer customer = createCustomer(CustomerStatus.ACTIVE);

        Vehicle vehicle = createVehicle(
                customer,
                VehicleStatus.INACTIVE
        );

        when(vehicleRepository.findById(VEHICLE_ID))
                .thenReturn(Optional.of(vehicle));

        when(vehicleRepository.save(vehicle))
                .thenReturn(vehicle);

        var response = vehicleService.activateVehicle(VEHICLE_ID);

        assertEquals(
                VehicleStatus.ACTIVE,
                response.getStatus()
        );

        assertEquals(VEHICLE_ID, response.getId());

        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void shouldRejectActivationForSuspendedCustomer() {

        Customer customer = createCustomer(CustomerStatus.SUSPENDED);

        Vehicle vehicle = createVehicle(
                customer,
                VehicleStatus.INACTIVE
        );

        when(vehicleRepository.findById(VEHICLE_ID))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.activateVehicle(VEHICLE_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                VehicleStatus.INACTIVE,
                vehicle.getStatus()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldRejectActivationWhenCustomerIsMissing() {

        Vehicle vehicle = createVehicle(
                null,
                VehicleStatus.INACTIVE
        );

        when(vehicleRepository.findById(VEHICLE_ID))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.activateVehicle(VEHICLE_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldRejectDuplicateVehicleNumber() {

        VehicleCreateRequest request = validCreateRequest();

        when(vehicleRepository.existsByVehicleNumber("ABC-2477"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(customerService);

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldRejectNegativeMileage() {

        VehicleCreateRequest request = validCreateRequest();

        request.setCurrentMileage(-100.0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldRejectNaNMileage() {

        VehicleCreateRequest request = validCreateRequest();

        request.setCurrentMileage(Double.NaN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldRejectInfiniteMileage() {

        VehicleCreateRequest request = validCreateRequest();

        request.setCurrentMileage(Double.POSITIVE_INFINITY);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void shouldRejectInvalidVehicleNumber() {

        VehicleCreateRequest request = validCreateRequest();

        request.setVehicleNumber("ABCD-1234");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    private VehicleCreateRequest validCreateRequest() {

        VehicleCreateRequest request = new VehicleCreateRequest();

        request.setCustomerId(CUSTOMER_ID);
        request.setVehicleNumber("ABC-2477");
        request.setVehicleType("Car");
        request.setBrand("Toyota");
        request.setModel("Aqua");
        request.setCurrentMileage(1256.2);

        return request;
    }

    private Customer createCustomer(CustomerStatus status) {

        Customer customer = new Customer();

        ReflectionTestUtils.setField(
                customer,
                "id",
                CUSTOMER_ID
        );

        customer.setStatus(status);

        return customer;
    }

    private Vehicle createVehicle(
            Customer customer,
            VehicleStatus status) {

        Vehicle vehicle = new Vehicle();

        ReflectionTestUtils.setField(
                vehicle,
                "id",
                VEHICLE_ID
        );

        vehicle.setCustomer(customer);
        vehicle.setVehicleNumber("ABC-2477");
        vehicle.setVehicleType("Car");
        vehicle.setStatus(status);

        return vehicle;
    }
}
