package com.richcomes.garage.history.integration;

import com.richcomes.garage.history.dto.ServiceHistoryCreateRequest;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.service.ServiceHistoryService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Integration adapter for Member 03 to automatically create service history
 * when a job is successfully completed/finalized.
 *
 * Member 03 should call this adapter after a job status transitions to COMPLETED or CLOSED.
 * This adapter handles:
 * - Duplicate prevention (unique job_id constraint)
 * - Validation of required job/vehicle/technician data
 * - Automatic reminder generation when next service date is provided
 * - Audit logging
 *
 * Contract for Member 03:
 * <pre>
 * 1. Call after job status is successfully set to COMPLETED or CLOSED
 * 2. Call within the same transaction as the job status update for consistency
 * 3. Provide all required job, vehicle, technician, and service information
 * 4. Do not call for CANCELLED or other non-completion statuses
 * </pre>
 */
@Component
public class JobCompletionHistoryAdapter {

    private final ServiceHistoryService serviceHistoryService;

    public JobCompletionHistoryAdapter(ServiceHistoryService serviceHistoryService) {
        this.serviceHistoryService = serviceHistoryService;
    }

    /**
     * Creates service history from a completed job.
     *
     * @param request job completion data from Member 03
     * @return created service history response
     * @throws IllegalArgumentException if required data is missing or invalid
     * @throws IllegalStateException if history already exists for this job
     */
    public ServiceHistoryResponse createHistoryFromJobCompletion(
            JobCompletionRequest request) {

        validateRequest(request);

        ServiceHistoryCreateRequest historyRequest = new ServiceHistoryCreateRequest();
        historyRequest.setJobId(request.getJobId());
        historyRequest.setVehicleId(request.getVehicleId());
        historyRequest.setServiceDate(request.getServiceDate());
        historyRequest.setOdometer(request.getOdometer());
        historyRequest.setSummary(request.getSummary());
        historyRequest.setTechnicianId(request.getTechnicianId());
        historyRequest.setInvoiceId(request.getInvoiceId());
        historyRequest.setTotalCost(request.getTotalCost());
        historyRequest.setNextServiceDate(request.getNextServiceDate());

        return serviceHistoryService.recordServiceHistory(historyRequest);
    }

    /**
     * Validates the job completion request.
     * Throws IllegalArgumentException if validation fails.
     */
    private void validateRequest(JobCompletionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Job completion request is required");
        }
        if (request.getJobId() == null || request.getJobId() <= 0) {
            throw new IllegalArgumentException("Valid job ID is required");
        }
        if (request.getVehicleId() == null || request.getVehicleId() <= 0) {
            throw new IllegalArgumentException("Valid vehicle ID is required");
        }
        if (request.getServiceDate() == null) {
            throw new IllegalArgumentException("Service date is required");
        }
        if (request.getOdometer() == null || request.getOdometer() < 0) {
            throw new IllegalArgumentException("Valid odometer reading is required");
        }
        if (request.getSummary() == null || request.getSummary().isBlank()) {
            throw new IllegalArgumentException("Service summary is required");
        }
        if (request.getTechnicianId() == null || request.getTechnicianId() <= 0) {
            throw new IllegalArgumentException("Valid technician ID is required");
        }
        if (request.getTotalCost() == null || request.getTotalCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid total cost is required");
        }
        if (request.getNextServiceDate() != null &&
                request.getNextServiceDate().isBefore(request.getServiceDate())) {
            throw new IllegalArgumentException("Next service date cannot be before service date");
        }
    }

    /**
     * Request DTO for job completion integration.
     * Member 03 should populate all fields from the completed job entity.
     */
    public static class JobCompletionRequest {
        private Long jobId;
        private Long vehicleId;
        private LocalDate serviceDate;
        private Integer odometer;
        private String summary;
        private Long technicianId;
        private Long invoiceId;
        private BigDecimal totalCost;
        private LocalDate nextServiceDate;

        public JobCompletionRequest() {}

        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }

        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

        public LocalDate getServiceDate() { return serviceDate; }
        public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }

        public Integer getOdometer() { return odometer; }
        public void setOdometer(Integer odometer) { this.odometer = odometer; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public Long getTechnicianId() { return technicianId; }
        public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }

        public Long getInvoiceId() { return invoiceId; }
        public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

        public BigDecimal getTotalCost() { return totalCost; }
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

        public LocalDate getNextServiceDate() { return nextServiceDate; }
        public void setNextServiceDate(LocalDate nextServiceDate) { this.nextServiceDate = nextServiceDate; }
    }
}