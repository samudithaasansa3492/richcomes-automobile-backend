
package com.richcomes.garage.appointment.controller;

import com.richcomes.garage.appointment.dto.AppointmentCreateRequest;
import com.richcomes.garage.appointment.dto.AppointmentResponse;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.service.AppointmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(
            AppointmentService appointmentService) {

        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody AppointmentCreateRequest request) {

        AppointmentResponse response =
                appointmentService.createAppointment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments() {

        return ResponseEntity.ok(
                appointmentService.getAllAppointments()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointmentById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentById(id)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByStatus(
            @PathVariable AppointmentStatus status) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentsByStatus(status)
        );
    }

    @GetMapping("/date/{requestedDate}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByDate(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate requestedDate) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentsByDate(requestedDate)
        );
    }

    @GetMapping("/mobile/{mobile}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByMobile(
            @PathVariable String mobile) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentsByMobile(mobile)
        );
    }

    @GetMapping("/vehicle/{vehicleNumber}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByVehicleNumber(
            @PathVariable String vehicleNumber) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentsByVehicleNumber(vehicleNumber)
        );
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<AppointmentResponse> approveAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentApproveRequest request) {

        return ResponseEntity.ok(
                appointmentService.approveAppointment(
                        id,
                        request.confirmedDate(),
                        request.appointmentTime(),
                        request.staffNotes()
                )
        );
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<AppointmentResponse> rejectAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentReasonRequest request) {

        return ResponseEntity.ok(
                appointmentService.rejectAppointment(
                        id,
                        request.reason(),
                        request.staffNotes()
                )
        );
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> completeAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentNotesRequest request) {

        return ResponseEntity.ok(
                appointmentService.completeAppointment(
                        id,
                        request.staffNotes()
                )
        );
    }

    @PatchMapping("/{id}/not-completed")
    public ResponseEntity<AppointmentResponse> markNotCompleted(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentReasonRequest request) {

        return ResponseEntity.ok(
                appointmentService.markNotCompleted(
                        id,
                        request.reason(),
                        request.staffNotes()
                )
        );
    }

    public record AppointmentApproveRequest(
            @NotNull LocalDate confirmedDate,
            @NotNull LocalTime appointmentTime,
            String staffNotes
    ) {
    }

    public record AppointmentReasonRequest(
            @NotBlank String reason,
            String staffNotes
    ) {
    }

    public record AppointmentNotesRequest(
            String staffNotes
    ) {
    }
}
