
package com.richcomes.garage.appointment.controller;

import com.richcomes.garage.appointment.dto.GuestAppointmentCreateRequest;
import com.richcomes.garage.appointment.service.AppointmentService;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/appointments")
public class GuestAppointmentController {

    private final AppointmentService appointmentService;

    public GuestAppointmentController(
            AppointmentService appointmentService) {

        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createAppointment(
            @Valid @RequestBody GuestAppointmentCreateRequest request) {

        appointmentService.createGuestAppointment(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "message",
                        "Your appointment request has been received and is pending staff confirmation."
                ));
    }
}
