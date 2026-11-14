package com.richcomes.garage.history.service;


import org.springframework.stereotype.Service;

@Service
public class LocalVehicleCustomerResolver
        implements VehicleCustomerResolver {

    @Override
    public Long resolveCustomerId(Long vehicleId) {
        if (vehicleId == null || vehicleId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid vehicle ID"
            );
        }

        return 1L;
    }
}