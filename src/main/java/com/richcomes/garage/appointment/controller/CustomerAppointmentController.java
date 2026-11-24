
package com.richcomes.garage.appointment.controller;

import com.richcomes.garage.appointment.dto.AppointmentResponse;
import com.richcomes.garage.appointment.dto.CustomerAppointmentCreateRequest;
import com.richcomes.garage.appointment.service.AppointmentService;
import com.richcomes.garage.auth.security.AccountPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/customer/appointments")
public class CustomerAppointmentController {

    private final AppointmentService appointmentService;

    public CustomerAppointmentController(
            AppointmentService appointmentService) {

        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAppointment(
            @Valid @RequestBody CustomerAppointmentCreateRequest request,
            @AuthenticationPrincipal AccountPrincipal principal) {

        AppointmentResponse created =
                appointmentService.createCustomerAppointment(
                        request,
                        principal
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "appointmentId", created.getId(),
                        "status", created.getStatus().name(),
                        "message", "Appointment request received. Await staff confirmation."
                ));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CustomerCancellationRequest request,
            @AuthenticationPrincipal AccountPrincipal principal) {

        AppointmentResponse cancelled =
                appointmentService.cancelCustomerAppointment(
                        id,
                        request.getReason(),
                        principal
                );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "appointmentId", cancelled.getId(),
                        "status", cancelled.getStatus().name(),
                        "message", "Your appointment has been cancelled successfully."
                ));
    }

    public static class CustomerCancellationRequest {

        @NotBlank(message = "Cancellation reason is required")
        @Size(
                max = 1000,
                message = "Cancellation reason cannot exceed 1000 characters"
        )
        private String reason;

        public CustomerCancellationRequest() {
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
