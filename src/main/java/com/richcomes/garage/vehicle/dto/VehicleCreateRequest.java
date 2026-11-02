
package com.richcomes.garage.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class VehicleCreateRequest {

    @NotBlank(message = "Vehicle number is required")
    @Pattern(
            regexp = "(?i)^\\s*(?:[A-Z]{2,3}|[0-9]{2,3})\\s*-\\s*[0-9]{4}\\s*$",
            message = "Vehicle number must have 2 or 3 letters or digits, a hyphen, and 4 digits"
    )
    private String vehicleNumber;

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    private Long customerId;

    @NotBlank(message = "Vehicle type is required")
    @Size(max = 50, message = "Vehicle type cannot exceed 50 characters")
    private String vehicleType;

    @Size(max = 60, message = "Brand cannot exceed 60 characters")
    private String brand;

    @Size(max = 80, message = "Model cannot exceed 80 characters")
    private String model;

    @Size(max = 40, message = "Colour cannot exceed 40 characters")
    private String colour;

    @Size(max = 80, message = "Engine number cannot exceed 80 characters")
    private String engineNumber;

    @Size(max = 80, message = "Chassis number cannot exceed 80 characters")
    private String chassisNumber;

    @Size(max = 50, message = "Tag number cannot exceed 50 characters")
    private String tagNumber;

    private LocalDate deliveryDate;

    @PositiveOrZero(message = "Current mileage cannot be negative")
    private Double currentMileage;

    public VehicleCreateRequest() {
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getEngineNumber() {
        return engineNumber;
    }

    public void setEngineNumber(String engineNumber) {
        this.engineNumber = engineNumber;
    }

    public String getChassisNumber() {
        return chassisNumber;
    }

    public void setChassisNumber(String chassisNumber) {
        this.chassisNumber = chassisNumber;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Double getCurrentMileage() {
        return currentMileage;
    }

    public void setCurrentMileage(Double currentMileage) {
        this.currentMileage = currentMileage;
    }
}
