package com.richcomes.garage.reminder.controller;

import com.richcomes.garage.reminder.dto.GenerateReminderRequest;
import com.richcomes.garage.reminder.dto.ServiceReminderCreateRequest;
import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import com.richcomes.garage.reminder.dto.ServiceReminderUpdateRequest;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import com.richcomes.garage.reminder.service.ServiceReminderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reminders")
public class ServiceReminderController {

    private final ServiceReminderService reminderService;

    public ServiceReminderController(ServiceReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    public ResponseEntity<ServiceReminderResponse> createReminder(
            @Valid @RequestBody ServiceReminderCreateRequest request) {
        ServiceReminderResponse response = reminderService.createReminder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/generate-from-history")
    public ResponseEntity<ServiceReminderResponse> generateFromHistory(
            @Valid @RequestBody GenerateReminderRequest request) {
        ServiceReminderResponse response = reminderService.generateFromServiceHistory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ServiceReminderResponse>> getAllReminders(
            @RequestParam(required = false) ServiceReminderStatus status) {
        return ResponseEntity.ok(reminderService.getAllReminders(status));
    }

    @GetMapping("/due")
    public ResponseEntity<List<ServiceReminderResponse>> getDueReminders() {
        return ResponseEntity.ok(reminderService.getDueReminders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceReminderResponse> getReminderById(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getReminderById(id));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<ServiceReminderResponse>> getRemindersByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(reminderService.getRemindersByVehicle(vehicleId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ServiceReminderResponse>> getRemindersByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(reminderService.getRemindersByCustomer(customerId));
    }

    @PatchMapping("/{id}/send")
    public ResponseEntity<ServiceReminderResponse> sendReminder(
            @PathVariable Long id,
            @RequestParam String recipient) {

        return ResponseEntity.ok(
                reminderService.sendReminder(id, recipient)
        );
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ServiceReminderResponse> cancelReminder(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.cancelReminder(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ServiceReminderResponse> updateReminder(
            @PathVariable Long id,
            @Valid @RequestBody ServiceReminderUpdateRequest request) {
        return ResponseEntity.ok(reminderService.updateReminder(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }
}
