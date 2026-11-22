package com.richcomes.garage.history.service;

import com.richcomes.garage.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class VehicleCustomerResolverImpl implements VehicleCustomerResolver {

    private static final Logger log = LoggerFactory.getLogger(VehicleCustomerResolverImpl.class);

    private final VehicleRepository vehicleRepository;

    public VehicleCustomerResolverImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Long resolveCustomerId(Long vehicleId) {
        if (vehicleId == null || vehicleId <= 0) {
            log.warn("Invalid vehicle ID: {}", vehicleId);
            return null;
        }

        try {
            return vehicleRepository.findById(vehicleId)
                    .map(vehicle -> {
                        if (vehicle.getCustomer() != null) {
                            return vehicle.getCustomer().getId();
                        }
                        log.warn("Vehicle {} has no associated customer", vehicleId);
                        return null;
                    })
                    .orElseGet(() -> {
                        log.warn("Vehicle not found: {}", vehicleId);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error resolving customer for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return null;
        }
    }
}