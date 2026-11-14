package com.richcomes.garage.history.service;

/** Implement this using the existing Vehicle/Customer service. */
public interface VehicleCustomerResolver {
    Long resolveCustomerId(Long vehicleId);
}