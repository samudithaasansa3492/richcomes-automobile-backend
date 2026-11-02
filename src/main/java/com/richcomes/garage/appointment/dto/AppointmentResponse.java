
package com.richcomes.garage.appointment.dto;

import com.richcomes.garage.appointment.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AppointmentResponse {

    private Long id;

    private String customerName;

    private String customerMobile;

    private String vehicleNumber;

    private Long customerId;

    private Long vehicleId;

    private LocalDate requestedDate;

    private LocalDate confirmedDate;

    private LocalTime appointmentTime;

    private String serviceDescription;

    private AppointmentStatus status;

    private LocalDateTime contactedAt;

    private String staffNotes;

    private String rejectionReason;

    private String notCompletedReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public AppointmentResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public LocalDate getConfirmedDate() {
        return confirmedDate;
    }

    public void setConfirmedDate(LocalDate confirmedDate) {
        this.confirmedDate = confirmedDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public LocalDateTime getContactedAt() {
        return contactedAt;
    }

    public void setContactedAt(LocalDateTime contactedAt) {
        this.contactedAt = contactedAt;
    }

    public String getStaffNotes() {
        return staffNotes;
    }

    public void setStaffNotes(String staffNotes) {
        this.staffNotes = staffNotes;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getNotCompletedReason() {
        return notCompletedReason;
    }

    public void setNotCompletedReason(String notCompletedReason) {
        this.notCompletedReason = notCompletedReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
