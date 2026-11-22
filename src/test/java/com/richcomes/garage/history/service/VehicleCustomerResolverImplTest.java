package com.richcomes.garage.history.service;

import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.vehicle.entity.Vehicle;
import com.richcomes.garage.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleCustomerResolverImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleCustomerResolverImpl resolver;

    private Vehicle vehicleWithCustomer;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(customer, 1L);
            java.lang.reflect.Field statusField = Customer.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(customer, CustomerStatus.ACTIVE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        vehicleWithCustomer = new Vehicle();
        try {
            java.lang.reflect.Field idField = Vehicle.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(vehicleWithCustomer, 10L);
            java.lang.reflect.Field customerField = Vehicle.class.getDeclaredField("customer");
            customerField.setAccessible(true);
            customerField.set(vehicleWithCustomer, customer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void resolveCustomerId_withValidVehicle_returnsCustomerId() {
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicleWithCustomer));

        Long customerId = resolver.resolveCustomerId(10L);

        assertEquals(1L, customerId);
    }

    @Test
    void resolveCustomerId_withNullVehicleId_returnsNull() {
        Long customerId = resolver.resolveCustomerId(null);
        assertNull(customerId);
    }

    @Test
    void resolveCustomerId_withZeroVehicleId_returnsNull() {
        Long customerId = resolver.resolveCustomerId(0L);
        assertNull(customerId);
    }

    @Test
    void resolveCustomerId_withNegativeVehicleId_returnsNull() {
        Long customerId = resolver.resolveCustomerId(-1L);
        assertNull(customerId);
    }

    @Test
    void resolveCustomerId_withVehicleNotFound_returnsNull() {
        when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());

        Long customerId = resolver.resolveCustomerId(999L);

        assertNull(customerId);
    }

    @Test
    void resolveCustomerId_withVehicleNoCustomer_returnsNull() {
        Vehicle vehicleNoCustomer = new Vehicle();
        try {
            java.lang.reflect.Field idField = Vehicle.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(vehicleNoCustomer, 20L);
            java.lang.reflect.Field customerField = Vehicle.class.getDeclaredField("customer");
            customerField.setAccessible(true);
            customerField.set(vehicleNoCustomer, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicleNoCustomer));

        Long customerId = resolver.resolveCustomerId(20L);

        assertNull(customerId);
    }

    @Test
    void resolveCustomerId_withRepositoryException_returnsNull() {
        when(vehicleRepository.findById(10L)).thenThrow(new RuntimeException("DB error"));

        Long customerId = resolver.resolveCustomerId(10L);

        assertNull(customerId);
    }
}