
package com.richcomes.garage.appointment.service;

import com.richcomes.garage.appointment.dto.AppointmentCreateRequest;
import com.richcomes.garage.appointment.dto.CustomerAppointmentCreateRequest;
import com.richcomes.garage.appointment.dto.GuestAppointmentCreateRequest;
import com.richcomes.garage.appointment.dto.AppointmentResponse;
import com.richcomes.garage.appointment.entity.Appointment;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.repository.AppointmentRepository;
import com.richcomes.garage.auth.security.AccountPrincipal;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.vehicle.entity.Vehicle;
import com.richcomes.garage.vehicle.enums.VehicleStatus;
import com.richcomes.garage.vehicle.service.VehicleService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional
public class AppointmentService {

    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("^07[0-9]{8}$");

    private static final Pattern VEHICLE_PATTERN =
            Pattern.compile(
                    "^(?:[A-Z]{2,3}|[0-9]{2,3})-[0-9]{4}$"
            );

    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;
    private final VehicleService vehicleService;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            CustomerService customerService,
            VehicleService vehicleService) {

        this.appointmentRepository = appointmentRepository;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
    }

    public AppointmentResponse createAppointment(
            AppointmentCreateRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Appointment details are required"
            );
        }

        Long customerId = request.getCustomerId();
        Long vehicleId = request.getVehicleId();

        if ((customerId == null) != (vehicleId == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer ID and Vehicle ID must be provided together"
            );
        }

        if (customerId != null && (customerId <= 0 || vehicleId <= 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer ID and Vehicle ID must be positive"
            );
        }

        String customerName = request.getCustomerName();

        if (customerName == null
                || customerName.isBlank()
                || customerName.trim().length() > 150) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer name is required"
            );
        }

        String mobile = request.getCustomerMobile();

        if (mobile == null
                || !MOBILE_PATTERN.matcher(mobile).matches()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid customer mobile number"
            );
        }

        String vehicleNumber =
                normalizeVehicleNumber(request.getVehicleNumber());

        if (request.getRequestedDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested date is required"
            );
        }

        if (request.getRequestedDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested date cannot be in the past"
            );
        }

        Appointment appointment = new Appointment();

        if (customerId != null) {
            Customer customer = customerService
                    .getCustomerEntityById(customerId);

            Vehicle vehicle = vehicleService
                    .getVehicleEntityById(vehicleId);

            if (customer.getStatus() != CustomerStatus.ACTIVE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Customer is not active"
                );
            }

            if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Vehicle is not active"
                );
            }

            if (vehicle.getCustomer() == null
                    || !customer.getId().equals(vehicle.getCustomer().getId())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Vehicle does not belong to the selected customer"
                );
            }

            if (!customer.getName().trim().equalsIgnoreCase(customerName.trim())
                    || !customer.getMobile().equals(mobile)
                    || !normalizeVehicleNumber(vehicle.getVehicleNumber())
                    .equals(vehicleNumber)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Booking details do not match registered records"
                );
            }

            appointment.setCustomer(customer);
            appointment.setVehicle(vehicle);
            appointment.setCustomerName(customer.getName().trim());
            appointment.setCustomerMobile(customer.getMobile());
            appointment.setVehicleNumber(
                    normalizeVehicleNumber(vehicle.getVehicleNumber())
            );
        } else {
            appointment.setCustomerName(customerName.trim());
            appointment.setCustomerMobile(mobile);
            appointment.setVehicleNumber(vehicleNumber);
        }

        appointment.setRequestedDate(request.getRequestedDate());
        appointment.setServiceDescription(
                normalizeOptional(request.getServiceDescription())
        );
        appointment.setStatus(AppointmentStatus.PENDING);

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        return mapToResponse(savedAppointment);
    }

    public AppointmentResponse createCustomerAppointment(
            CustomerAppointmentCreateRequest request,
            AccountPrincipal principal) {

        if (principal == null
                || principal.customerId() == null
                || !principal.roles().equals(Set.of("CUSTOMER"))
                || !principal.permissions().contains("customer:self")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer account authentication is required"
            );
        }

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Appointment details are required"
            );
        }

        if (request.getVehicleId() == null || request.getVehicleId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid vehicle ID is required"
            );
        }

        LocalDate requestedDate = request.getRequestedDate();

        if (requestedDate == null || requestedDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested date must be today or a future date"
            );
        }

        String serviceDescription = request.getServiceDescription();

        if (serviceDescription != null && serviceDescription.length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service description cannot exceed 1000 characters"
            );
        }

        Customer customer = customerService
                .getCustomerEntityById(principal.customerId());

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer account is not active"
            );
        }

        Vehicle vehicle = vehicleService
                .getVehicleEntityById(request.getVehicleId());

        if (vehicle.getCustomer() == null
                || !customer.getId().equals(vehicle.getCustomer().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Vehicle not found for this customer"
            );
        }

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vehicle is not active"
            );
        }

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setCustomerName(customer.getName().trim());
        appointment.setCustomerMobile(customer.getMobile());
        appointment.setVehicleNumber(
                normalizeVehicleNumber(vehicle.getVehicleNumber())
        );
        appointment.setRequestedDate(requestedDate);
        appointment.setServiceDescription(normalizeOptional(serviceDescription));
        appointment.setStatus(AppointmentStatus.PENDING);

        return mapToResponse(appointmentRepository.save(appointment));
    }

    public void createGuestAppointment(GuestAppointmentCreateRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Appointment details are required"
            );
        }

        String customerName = request.getCustomerName();

        if (customerName == null
                || customerName.isBlank()
                || customerName.trim().length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer name is required"
            );
        }

        String mobile = request.getCustomerMobile();

        if (mobile == null
                || !MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid customer mobile number"
            );
        }

        String vehicleNumber =
                normalizeVehicleNumber(request.getVehicleNumber());

        LocalDate requestedDate = request.getRequestedDate();

        if (requestedDate == null
                || requestedDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested date must be today or a future date"
            );
        }

        String serviceDescription = request.getServiceDescription();

        if (serviceDescription != null
                && serviceDescription.length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service description cannot exceed 1000 characters"
            );
        }

        Appointment appointment = new Appointment();
        appointment.setCustomerName(customerName.trim());
        appointment.setCustomerMobile(mobile);
        appointment.setVehicleNumber(vehicleNumber);
        appointment.setRequestedDate(requestedDate);
        appointment.setServiceDescription(
                normalizeOptional(serviceDescription)
        );
        appointment.setStatus(AppointmentStatus.PENDING);

        appointmentRepository.save(appointment);
    }

    public AppointmentResponse cancelCustomerAppointment(
            Long id,
            String cancellationReason,
            AccountPrincipal principal) {

        if (principal == null
                || principal.accountId() == null
                || principal.customerId() == null
                || !principal.roles().equals(Set.of("CUSTOMER"))
                || !principal.permissions().contains("customer:self")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer authentication is required"
            );
        }

        String reason = validateCancellationReason(cancellationReason);
        Appointment appointment = findAppointmentByIdForUpdate(id);

        if (appointment.getCustomer() == null
                || !principal.customerId().equals(
                appointment.getCustomer().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Appointment not found"
            );
        }

        requireStatus(appointment, AppointmentStatus.PENDING);

        applyCancellation(
                appointment,
                reason,
                principal.accountId(),
                "CUSTOMER"
        );

        return mapToResponse(appointmentRepository.save(appointment));
    }

    public AppointmentResponse cancelStaffAppointment(
            Long id,
            String cancellationReason,
            AccountPrincipal principal) {

        if (principal == null
                || principal.accountId() == null
                || !(principal.roles().contains("OWNER")
                || principal.roles().contains("CASHIER"))
                || !principal.permissions().contains("appointment:manage")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Appointment management permission is required"
            );
        }

        String reason = validateCancellationReason(cancellationReason);
        Appointment appointment = findAppointmentByIdForUpdate(id);

        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending or approved appointments can be cancelled"
            );
        }

        applyCancellation(
                appointment,
                reason,
                principal.accountId(),
                "STAFF"
        );

        return mapToResponse(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {

        return appointmentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getTodayApprovedAppointments() {

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));

        return appointmentRepository
                .findByConfirmedDateAndStatusOrderByAppointmentTimeAscIdAsc(
                        today,
                        AppointmentStatus.APPROVED
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getApprovedAppointmentsByConfirmedDate(
            LocalDate confirmedDate) {

        if (confirmedDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Confirmed date is required"
            );
        }

        return appointmentRepository
                .findByConfirmedDateAndStatusOrderByAppointmentTimeAscIdAsc(
                        confirmedDate,
                        AppointmentStatus.APPROVED
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {

        return mapToResponse(findAppointmentById(id));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByStatus(
            AppointmentStatus status) {

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Appointment status is required"
            );
        }

        return appointmentRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDate(
            LocalDate requestedDate) {

        if (requestedDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested date is required"
            );
        }

        return appointmentRepository
                .findByRequestedDate(requestedDate)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByMobile(
            String mobile) {

        if (mobile == null
                || !MOBILE_PATTERN.matcher(mobile).matches()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid mobile number"
            );
        }

        return appointmentRepository
                .findByCustomerMobile(mobile)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByVehicleNumber(
            String vehicleNumber) {

        String normalizedNumber =
                normalizeVehicleNumber(vehicleNumber);

        return appointmentRepository
                .findByVehicleNumber(normalizedNumber)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AppointmentResponse approveAppointment(
            Long id,
            LocalDate confirmedDate,
            LocalTime appointmentTime,
            String staffNotes) {

        Appointment appointment = findAppointmentByIdForUpdate(id);

        requireStatus(appointment, AppointmentStatus.PENDING);

        if (confirmedDate == null || appointmentTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Confirmed date and appointment time are required"
            );
        }

        LocalDateTime schedule =
                LocalDateTime.of(confirmedDate, appointmentTime);

        if (schedule.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Confirmed appointment schedule cannot be in the past"
            );
        }

        appointment.setConfirmedDate(confirmedDate);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setStaffNotes(normalizeOptional(staffNotes));
        appointment.setContactedAt(LocalDateTime.now());
        appointment.setStatus(AppointmentStatus.APPROVED);

        return mapToResponse(appointmentRepository.save(appointment));
    }

    public AppointmentResponse rejectAppointment(
            Long id,
            String rejectionReason,
            String staffNotes) {

        Appointment appointment = findAppointmentByIdForUpdate(id);

        requireStatus(appointment, AppointmentStatus.PENDING);

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required"
            );
        }

        appointment.setRejectionReason(rejectionReason.trim());
        appointment.setStaffNotes(normalizeOptional(staffNotes));
        appointment.setContactedAt(LocalDateTime.now());
        appointment.setStatus(AppointmentStatus.REJECTED);

        return mapToResponse(appointmentRepository.save(appointment));
    }

    public AppointmentResponse completeAppointment(
            Long id,
            String staffNotes) {

        Appointment appointment = findAppointmentByIdForUpdate(id);

        requireStatus(appointment, AppointmentStatus.APPROVED);

        appointment.setStaffNotes(normalizeOptional(staffNotes));
        appointment.setStatus(AppointmentStatus.DONE);

        return mapToResponse(appointmentRepository.save(appointment));
    }

    public AppointmentResponse markNotCompleted(
            Long id,
            String reason,
            String staffNotes) {

        Appointment appointment = findAppointmentByIdForUpdate(id);

        requireStatus(appointment, AppointmentStatus.APPROVED);

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Not completed reason is required"
            );
        }

        appointment.setNotCompletedReason(reason.trim());
        appointment.setStaffNotes(normalizeOptional(staffNotes));
        appointment.setStatus(AppointmentStatus.NOT_COMPLETED);

        return mapToResponse(appointmentRepository.save(appointment));
    }

    private Appointment findAppointmentById(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid appointment ID is required"
            );
        }

        return appointmentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Appointment not found"
                        )
                );
    }

    private Appointment findAppointmentByIdForUpdate(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid appointment ID is required"
            );
        }

        return appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Appointment not found"
                        )
                );
    }

    private String validateCancellationReason(String reason) {

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cancellation reason is required"
            );
        }

        String normalized = reason.trim();

        if (normalized.length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cancellation reason cannot exceed 1000 characters"
            );
        }

        return normalized;
    }

    private void applyCancellation(
            Appointment appointment,
            String cancellationReason,
            Long actorAccountId,
            String actorType) {

        appointment.setCancellationReason(cancellationReason);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelledByAccountId(actorAccountId);
        appointment.setCancelledByType(actorType);
        appointment.setStatus(AppointmentStatus.CANCELLED);
    }

    private void requireStatus(
            Appointment appointment,
            AppointmentStatus requiredStatus) {

        if (appointment.getStatus() != requiredStatus) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid appointment status transition"
            );
        }
    }

    private String normalizeVehicleNumber(String value) {

        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle number is required"
            );
        }

        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s*-\\s*", "-");

        if (!VEHICLE_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid vehicle registration number"
            );
        }

        return normalized;
    }

    private String normalizeOptional(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private AppointmentResponse mapToResponse(
            Appointment appointment) {

        AppointmentResponse response = new AppointmentResponse();

        response.setId(appointment.getId());
        response.setCustomerName(appointment.getCustomerName());
        response.setCustomerMobile(appointment.getCustomerMobile());
        response.setVehicleNumber(appointment.getVehicleNumber());

        response.setCustomerId(
                appointment.getCustomer() != null
                        ? appointment.getCustomer().getId()
                        : null
        );

        response.setVehicleId(
                appointment.getVehicle() != null
                        ? appointment.getVehicle().getId()
                        : null
        );

        response.setRequestedDate(appointment.getRequestedDate());
        response.setConfirmedDate(appointment.getConfirmedDate());
        response.setAppointmentTime(appointment.getAppointmentTime());
        response.setServiceDescription(appointment.getServiceDescription());
        response.setStatus(appointment.getStatus());
        response.setContactedAt(appointment.getContactedAt());
        response.setStaffNotes(appointment.getStaffNotes());
        response.setRejectionReason(appointment.getRejectionReason());
        response.setNotCompletedReason(appointment.getNotCompletedReason());
        response.setCancellationReason(appointment.getCancellationReason());
        response.setCancelledAt(appointment.getCancelledAt());
        response.setCancelledByAccountId(appointment.getCancelledByAccountId());
        response.setCancelledByType(appointment.getCancelledByType());
        response.setCreatedAt(appointment.getCreatedAt());
        response.setUpdatedAt(appointment.getUpdatedAt());

        return response;
    }
}
