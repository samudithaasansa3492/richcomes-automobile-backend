
package com.richcomes.garage.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class AppointmentCreateRequest {

    @NotBlank(message = "Customer name is required")
    @Size(
            max = 150,
            message = "Customer name cannot exceed 150 characters"
    )
    private String customerName;

    @NotBlank(message = "Customer mobile number is required")
    @Pattern(
            regexp = "^07[0-9]{8}$",
            message = "Enter a valid Sri Lankan mobile number (07XXXXXXXX)"
    )
    private String customerMobile;

    @NotBlank(message = "Vehicle number is required")
    @Pattern(
            regexp = "(?i)^\\s*(?:[A-Z]{2,3}|[0-9]{2,3})\\s*-\\s*[0-9]{4}\\s*$",
            message = "Invalid vehicle registration number"
    )
    private String vehicleNumber;

    @NotNull(message = "Requested date is required")
    private LocalDate requestedDate;

    private String serviceDescription;

    public AppointmentCreateRequest() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerMobile() {
        return customerMobile;
    }

    public void setCustomerMobile(String customerMobile) {
        this.customerMobile = customerMobile;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }
}
