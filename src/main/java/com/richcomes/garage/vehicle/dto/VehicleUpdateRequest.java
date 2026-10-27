
package com.richcomes.garage.vehicle.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class VehicleUpdateRequest {

    @Pattern(
            regexp = ".*\\S.*",
            message = "Vehicle type cannot be blank"
    )
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
    private Integer currentMileage;

    public VehicleUpdateRequest() {
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

    public Integer getCurrentMileage() {
        return currentMileage;
    }

    public void setCurrentMileage(Integer currentMileage) {
        this.currentMileage = currentMileage;
    }
}
