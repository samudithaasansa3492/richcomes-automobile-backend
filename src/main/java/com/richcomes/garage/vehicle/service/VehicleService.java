
package com.richcomes.garage.vehicle.service;

import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.vehicle.dto.VehicleCreateRequest;
import com.richcomes.garage.vehicle.dto.VehicleResponse;
import com.richcomes.garage.vehicle.dto.VehicleUpdateRequest;
import com.richcomes.garage.vehicle.entity.Vehicle;
import com.richcomes.garage.vehicle.enums.VehicleStatus;
import com.richcomes.garage.vehicle.repository.VehicleRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CustomerService customerService;

    public VehicleService(
            VehicleRepository vehicleRepository,
            CustomerService customerService) {

        this.vehicleRepository = vehicleRepository;
        this.customerService = customerService;
    }

    // CREATE VEHICLE
    public VehicleResponse createVehicle(VehicleCreateRequest request) {

        if (request == null
                || request.getVehicleNumber() == null
                || request.getVehicleNumber().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle number is required"
            );
        }

        if (request.getVehicleType() == null
                || request.getVehicleType().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle type is required"
            );
        }

        if (request.getCustomerId() == null
                || request.getCustomerId() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer ID is required"
            );
        }

        if (request.getCurrentMileage() != null
                && request.getCurrentMileage() < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current mileage cannot be negative"
            );
        }

        String vehicleNumber =
                normalizeVehicleNumber(request.getVehicleNumber());

        // CHECK DUPLICATE VEHICLE NUMBER
        if (vehicleRepository.existsByVehicleNumber(vehicleNumber)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vehicle number already exists"
            );
        }

        // FIND EXISTING CUSTOMER
        Customer customer = customerService
                .getCustomerEntityById(request.getCustomerId());

        // CREATE NEW VEHICLE
        Vehicle vehicle = new Vehicle();

        vehicle.setVehicleNumber(vehicleNumber);
        vehicle.setCustomer(customer);
        vehicle.setVehicleType(request.getVehicleType().trim());

        vehicle.setBrand(normalizeOptional(request.getBrand()));
        vehicle.setModel(normalizeOptional(request.getModel()));
        vehicle.setColour(normalizeOptional(request.getColour()));
        vehicle.setEngineNumber(normalizeOptional(request.getEngineNumber()));
        vehicle.setChassisNumber(normalizeOptional(request.getChassisNumber()));
        vehicle.setTagNumber(normalizeOptional(request.getTagNumber()));

        vehicle.setDeliveryDate(request.getDeliveryDate());
        vehicle.setCurrentMileage(request.getCurrentMileage());
        vehicle.setStatus(VehicleStatus.ACTIVE);

        Vehicle savedVehicle = vehicleRepository.save(vehicle);

        return mapToResponse(savedVehicle);
    }

    // GET ALL VEHICLES
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles() {

        return vehicleRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET VEHICLE BY ID
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {

        Vehicle vehicle = findVehicleById(id);

        return mapToResponse(vehicle);
    }

    // SEARCH VEHICLE BY REGISTRATION NUMBER
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleByNumber(String vehicleNumber) {

        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle number is required"
            );
        }

        String normalizedNumber =
                normalizeVehicleNumber(vehicleNumber);

        Vehicle vehicle = vehicleRepository
                .findByVehicleNumber(normalizedNumber)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Vehicle not found"
                        )
                );

        return mapToResponse(vehicle);
    }

    // GET VEHICLES BY CUSTOMER ID
    @Transactional(readOnly = true)
    public List<VehicleResponse> getVehiclesByCustomer(Long customerId) {

        if (customerId == null || customerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer ID is required"
            );
        }

        // VALIDATE CUSTOMER EXISTS
        customerService.getCustomerEntityById(customerId);

        return vehicleRepository.findByCustomer_Id(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // UPDATE VEHICLE
    public VehicleResponse updateVehicle(
            Long id,
            VehicleUpdateRequest request) {

        Vehicle vehicle = findVehicleById(id);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle update data is required"
            );
        }

        if (request.getVehicleType() != null) {

            if (request.getVehicleType().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vehicle type cannot be empty"
                );
            }

            vehicle.setVehicleType(
                    request.getVehicleType().trim()
            );
        }

        if (request.getBrand() != null) {
            vehicle.setBrand(
                    normalizeOptional(request.getBrand())
            );
        }

        if (request.getModel() != null) {
            vehicle.setModel(
                    normalizeOptional(request.getModel())
            );
        }

        if (request.getColour() != null) {
            vehicle.setColour(
                    normalizeOptional(request.getColour())
            );
        }

        if (request.getEngineNumber() != null) {
            vehicle.setEngineNumber(
                    normalizeOptional(request.getEngineNumber())
            );
        }

        if (request.getChassisNumber() != null) {
            vehicle.setChassisNumber(
                    normalizeOptional(request.getChassisNumber())
            );
        }

        if (request.getTagNumber() != null) {
            vehicle.setTagNumber(
                    normalizeOptional(request.getTagNumber())
            );
        }

        if (request.getDeliveryDate() != null) {
            vehicle.setDeliveryDate(
                    request.getDeliveryDate()
            );
        }

        if (request.getCurrentMileage() != null) {

            if (request.getCurrentMileage() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Current mileage cannot be negative"
                );
            }

            vehicle.setCurrentMileage(
                    request.getCurrentMileage()
            );
        }

        Vehicle updatedVehicle = vehicleRepository.save(vehicle);

        return mapToResponse(updatedVehicle);
    }

    // DEACTIVATE VEHICLE
    public VehicleResponse deactivateVehicle(Long id) {

        Vehicle vehicle = findVehicleById(id);

        vehicle.setStatus(VehicleStatus.INACTIVE);

        Vehicle updatedVehicle = vehicleRepository.save(vehicle);

        return mapToResponse(updatedVehicle);
    }

    // ACTIVATE VEHICLE
    public VehicleResponse activateVehicle(Long id) {

        Vehicle vehicle = findVehicleById(id);

        vehicle.setStatus(VehicleStatus.ACTIVE);

        Vehicle updatedVehicle = vehicleRepository.save(vehicle);

        return mapToResponse(updatedVehicle);
    }

    // FIND VEHICLE ENTITY BY ID
    private Vehicle findVehicleById(Long id) {

        return vehicleRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Vehicle not found"
                        )
                );
    }

    // NORMALIZE VEHICLE NUMBER
    private String normalizeVehicleNumber(String value) {

        return value.trim().toUpperCase(Locale.ROOT);
    }

    // NORMALIZE OPTIONAL FIELDS
    private String normalizeOptional(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // MAP ENTITY TO RESPONSE DTO
    private VehicleResponse mapToResponse(Vehicle vehicle) {

        VehicleResponse response = new VehicleResponse();

        response.setId(vehicle.getId());
        response.setVehicleNumber(vehicle.getVehicleNumber());
        response.setCustomerId(vehicle.getCustomer().getId());
        response.setVehicleType(vehicle.getVehicleType());
        response.setBrand(vehicle.getBrand());
        response.setModel(vehicle.getModel());
        response.setColour(vehicle.getColour());
        response.setEngineNumber(vehicle.getEngineNumber());
        response.setChassisNumber(vehicle.getChassisNumber());
        response.setTagNumber(vehicle.getTagNumber());
        response.setDeliveryDate(vehicle.getDeliveryDate());
        response.setCurrentMileage(vehicle.getCurrentMileage());
        response.setStatus(vehicle.getStatus());
        response.setCreatedAt(vehicle.getCreatedAt());
        response.setUpdatedAt(vehicle.getUpdatedAt());

        return response;
    }
}
