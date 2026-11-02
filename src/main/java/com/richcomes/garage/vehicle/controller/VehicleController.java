
package com.richcomes.garage.vehicle.controller;

import com.richcomes.garage.vehicle.dto.VehicleCreateRequest;
import com.richcomes.garage.vehicle.dto.VehicleResponse;
import com.richcomes.garage.vehicle.dto.VehicleUpdateRequest;
import com.richcomes.garage.vehicle.service.VehicleService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody VehicleCreateRequest request) {

        VehicleResponse response =
                vehicleService.createVehicle(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> getAllVehicles() {

        return ResponseEntity.ok(
                vehicleService.getAllVehicles()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                vehicleService.getVehicleById(id)
        );
    }

    @GetMapping("/number/{vehicleNumber}")
    public ResponseEntity<VehicleResponse> getVehicleByNumber(
            @PathVariable String vehicleNumber) {

        return ResponseEntity.ok(
                vehicleService.getVehicleByNumber(vehicleNumber)
        );
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<VehicleResponse>> getVehiclesByCustomer(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                vehicleService.getVehiclesByCustomer(customerId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody VehicleUpdateRequest request) {

        return ResponseEntity.ok(
                vehicleService.updateVehicle(id, request)
        );
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<VehicleResponse> deactivateVehicle(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                vehicleService.deactivateVehicle(id)
        );
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<VehicleResponse> activateVehicle(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                vehicleService.activateVehicle(id)
        );
    }
}
