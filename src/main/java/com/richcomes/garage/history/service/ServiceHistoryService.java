package com.richcomes.garage.history.service;

import com.richcomes.garage.audit.service.AuditLogService;
import com.richcomes.garage.history.dto.ServiceHistoryCreateRequest;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.dto.ServiceHistoryUpdateRequest;
//import com.richcomes.garage.history.service.VehicleCustomerResolver;
import com.richcomes.garage.history.entity.ServiceHistory;
import com.richcomes.garage.history.repository.ServiceHistoryRepository;
import com.richcomes.garage.reminder.service.ServiceReminderService;
import com.richcomes.garage.reminder.dto.GenerateReminderRequest;

//import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ServiceHistoryService {


    private final ServiceHistoryRepository serviceHistoryRepository;
    private final AuditLogService auditLogService;
    private final ServiceReminderService serviceReminderService;
    private final VehicleCustomerResolver vehicleCustomerResolver;

    public ServiceHistoryService(
            ServiceHistoryRepository serviceHistoryRepository,
            AuditLogService auditLogService,
            ServiceReminderService serviceReminderService,
            VehicleCustomerResolver vehicleCustomerResolver) {

        this.serviceHistoryRepository = serviceHistoryRepository;
        this.auditLogService = auditLogService;
        this.serviceReminderService = serviceReminderService;
        this.vehicleCustomerResolver = vehicleCustomerResolver;
    }

    // =====================================================
    // CREATE SERVICE HISTORY
    // =====================================================

    /**
     * Records service history for a finalized job.
     * The calling workflow must verify that the job
     * has actually been finalized.
     */
    public ServiceHistoryResponse recordServiceHistory(
            ServiceHistoryCreateRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body is required"
            );
        }

        validateId(request.getJobId(), "Job");
        validateId(request.getVehicleId(), "Vehicle");

        if (request.getServiceDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service date is required"
            );
        }

        if (request.getSummary() == null ||
                request.getSummary().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service summary is required"
            );
        }

        if (serviceHistoryRepository.existsByJobId(
                request.getJobId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Service history for Job ID "
                            + request.getJobId()
                            + " already exists"
            );
        }

        if (request.getNextServiceDate() != null &&
                request.getNextServiceDate()
                        .isBefore(request.getServiceDate())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Next service date cannot be earlier "
                            + "than service date"
            );
        }

        ServiceHistory history = new ServiceHistory();

        history.setVehicleId(request.getVehicleId());
        history.setJobId(request.getJobId());
        history.setServiceDate(request.getServiceDate());
        history.setOdometer(request.getOdometer());
        history.setSummary(request.getSummary().trim());
        history.setTechnicianId(request.getTechnicianId());
        history.setInvoiceId(request.getInvoiceId());
        history.setTotalCost(request.getTotalCost());
        history.setNextServiceDate(request.getNextServiceDate());

        ServiceHistory saved =
                serviceHistoryRepository.save(history);

        // Generate a reminder only when the service date allows a full 7-day lead.
        // The Vehicle module supplies the customer ID via the resolver.
        if (saved.getNextServiceDate() != null
                && !saved.getNextServiceDate().minusDays(7).isBefore(LocalDate.now())) {
            Long customerId = vehicleCustomerResolver.resolveCustomerId(saved.getVehicleId());
            if (customerId == null || customerId <= 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No customer is associated with this vehicle");
            }
            GenerateReminderRequest reminderRequest = new GenerateReminderRequest();
            reminderRequest.setServiceHistoryId(saved.getId());
            reminderRequest.setCustomerId(customerId);
            serviceReminderService.generateFromServiceHistory(reminderRequest);
        }

        return mapToResponse(saved);
    }

    // =====================================================
    // GET ALL SERVICE HISTORIES
    // =====================================================

    @Transactional(readOnly = true)
    public Page<ServiceHistoryResponse> getAllServiceHistories(
            int page, int size) {

        validatePagination(page, size);

        Pageable pageable = createPageable(page, size);

        return serviceHistoryRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    // =====================================================
    // GET HISTORY BY ID
    // =====================================================

    @Transactional(readOnly = true)
    public ServiceHistoryResponse getServiceHistoryById(Long id) {

        ServiceHistory history = findHistoryById(id);

        return mapToResponse(history);
    }

    // =====================================================
    // GET HISTORY BY VEHICLE
    // =====================================================

    @Transactional(readOnly = true)
    public List<ServiceHistoryResponse> getServiceHistoryByVehicleId(
            Long vehicleId) {

        validateId(vehicleId, "Vehicle");

        return serviceHistoryRepository
                .findByVehicleIdOrderByServiceDateDesc(vehicleId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =====================================================
    // GET HISTORY BY JOB
    // =====================================================

    @Transactional(readOnly = true)
    public ServiceHistoryResponse getServiceHistoryByJobId(
            Long jobId) {

        validateId(jobId, "Job");

        ServiceHistory history =
                serviceHistoryRepository.findByJobId(jobId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Service history not found for Job ID "
                                                + jobId
                                )
                        );

        return mapToResponse(history);
    }

    // =====================================================
    // GET HISTORY BY TECHNICIAN
    // =====================================================

    @Transactional(readOnly = true)
    public List<ServiceHistoryResponse> getServiceHistoryByTechnician(
            Long technicianId) {

        validateId(technicianId, "Technician");

        return serviceHistoryRepository
                .findByTechnicianIdOrderByServiceDateDesc(technicianId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =====================================================
    // GET HISTORY BY DATE RANGE
    // =====================================================

    @Transactional(readOnly = true)
    public Page<ServiceHistoryResponse> getServiceHistoryByDateRange(
            LocalDate start,
            LocalDate end,
            int page,
            int size) {

        validatePagination(page, size);

        if (start == null || end == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date and end date are required"
            );
        }

        if (start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date cannot be after end date"
            );
        }

        Pageable pageable = createPageable(page, size);

        return serviceHistoryRepository
                .findByServiceDateBetween(start, end, pageable)
                .map(this::mapToResponse);
    }

    // =====================================================
    // UPDATE SERVICE HISTORY
    // =====================================================

    /**
     * Updates a service history record and creates
     * an audit log in the same transaction.
     * actorUserId must be obtained from authenticated
     * server-side identity, not a client-provided ID.
     */
    public ServiceHistoryResponse updateServiceHistory(
            Long id,
            ServiceHistoryUpdateRequest request,
            Long actorUserId,
            String reason,
            String ipAddress) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Update request is required"
            );
        }

        validateId(actorUserId, "Actor user");

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reason for updating service history is required"
            );
        }

        ServiceHistory history = findHistoryById(id);

        // Capture original values before modifying entity.
        String oldValue = historySnapshot(history);

        if (request.getOdometer() != null) {
            history.setOdometer(request.getOdometer());
        }

        if (request.getSummary() != null) {

            if (request.getSummary().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Summary cannot be blank"
                );
            }

            history.setSummary(request.getSummary().trim());
        }

        if (request.getInvoiceId() != null) {
            history.setInvoiceId(request.getInvoiceId());
        }

        if (request.getTotalCost() != null) {
            history.setTotalCost(request.getTotalCost());
        }

        if (request.getNextServiceDate() != null) {

            if (request.getNextServiceDate()
                    .isBefore(history.getServiceDate())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Next service date cannot be "
                                + "before service date"
                );
            }

            history.setNextServiceDate(
                    request.getNextServiceDate()
            );
        }

        String newValue = historySnapshot(history);

        if (Objects.equals(oldValue, newValue)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No service history changes were provided"
            );
        }

        ServiceHistory updated =
                serviceHistoryRepository.save(history);

        auditLogService.logEvent(
                actorUserId,
                "UPDATE",
                "SERVICE_HISTORY",
                updated.getId(),
                oldValue,
                newValue,
                "Service history updated: " + reason.trim(),
                ipAddress
        );

        return mapToResponse(updated);
    }

    // =====================================================
    // DELETE SERVICE HISTORY - UNCHANGED AS REQUESTED
    // =====================================================

    public void deleteServiceHistory(Long id) {

        ServiceHistory history = findHistoryById(id);

        serviceHistoryRepository.delete(history);
    }

    // =====================================================
    // VALIDATION HELPERS
    // =====================================================

    private void validatePagination(int page, int size) {

        if (page < 0 || size < 1 || size > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size must be "
                            + "between 1 and 100"
            );
        }
    }

    private void validateId(Long id, String fieldName) {

        if (id == null || id <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " ID must be positive"
            );
        }
    }

    private Pageable createPageable(int page, int size) {

        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "serviceDate")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    // =====================================================
    // AUDIT SNAPSHOT HELPER
    // =====================================================

    private String historySnapshot(ServiceHistory history) {

        return "odometer=" + history.getOdometer()
                + ", summary=" + history.getSummary()
                + ", invoiceId=" + history.getInvoiceId()
                + ", totalCost=" + history.getTotalCost()
                + ", nextServiceDate=" + history.getNextServiceDate();
    }

    // =====================================================
    // FIND HISTORY
    // =====================================================

    private ServiceHistory findHistoryById(Long id) {

        validateId(id, "History");

        return serviceHistoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Service history with ID "
                                        + id + " not found"
                        )
                );
    }

    // =====================================================
    // ENTITY TO RESPONSE DTO
    // =====================================================

    public ServiceHistoryResponse mapToResponse(
            ServiceHistory history) {

        ServiceHistoryResponse response =
                new ServiceHistoryResponse();

        response.setId(history.getId());
        response.setVehicleId(history.getVehicleId());
        response.setJobId(history.getJobId());
        response.setServiceDate(history.getServiceDate());
        response.setOdometer(history.getOdometer());
        response.setSummary(history.getSummary());
        response.setTechnicianId(history.getTechnicianId());
        response.setInvoiceId(history.getInvoiceId());
        response.setTotalCost(history.getTotalCost());
        response.setNextServiceDate(history.getNextServiceDate());
        response.setCreatedAt(history.getCreatedAt());
        response.setUpdatedAt(history.getUpdatedAt());

        return response;
    }
}
