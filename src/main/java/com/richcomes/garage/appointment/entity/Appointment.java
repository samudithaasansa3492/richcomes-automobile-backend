
package com.richcomes.garage.appointment.entity;

import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.vehicle.entity.Vehicle;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Customer name is required")
    @Size(max = 150)
    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @NotBlank(message = "Customer mobile number is required")
    @Pattern(
            regexp = "^07[0-9]{8}$",
            message = "Enter a valid Sri Lankan mobile number"
    )
    @Column(name = "customer_mobile", nullable = false, length = 10)
    private String customerMobile;

    @NotBlank(message = "Vehicle number is required")
    @Pattern(
            regexp = "^(?:[A-Z]{2,3}|[0-9]{2,3})-[0-9]{4}$",
            message = "Invalid vehicle registration number"
    )
    @Column(name = "vehicle_number", nullable = false, length = 8)
    private String vehicleNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @NotNull(message = "Requested date is required")
    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "confirmed_date")
    private LocalDate confirmedDate;

    @Column(name = "appointment_time")
    private LocalTime appointmentTime;

    @Column(name = "service_description")
    private String serviceDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "contacted_at")
    private LocalDateTime contactedAt;

    @Column(name = "staff_notes")
    private String staffNotes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "not_completed_reason")
    private String notCompletedReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Appointment() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = AppointmentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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
        if (vehicleNumber == null) {
            this.vehicleNumber = null;
            return;
        }

        this.vehicleNumber = vehicleNumber
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s*-\\s*", "-");
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
