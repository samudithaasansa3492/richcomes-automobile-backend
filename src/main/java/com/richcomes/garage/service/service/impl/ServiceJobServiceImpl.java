package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.history.integration.JobCompletionHistoryAdapter;
import com.richcomes.garage.history.integration.JobCompletionHistoryAdapter.JobCompletionRequest;
import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.repository.ServiceJobRepository;
import com.richcomes.garage.service.service.ServiceJobService;
import com.richcomes.garage.vehicle.service.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceJobServiceImpl implements ServiceJobService {

    private static final Logger log = LoggerFactory.getLogger(ServiceJobServiceImpl.class);

    private final ServiceJobRepository repository;
    private final VehicleService vehicleService;
    private final JobCompletionHistoryAdapter historyAdapter;

    @Autowired
    public ServiceJobServiceImpl(ServiceJobRepository repository,
                                 VehicleService vehicleService,
                                 JobCompletionHistoryAdapter historyAdapter) {
        this.repository = repository;
        this.vehicleService = vehicleService;
        this.historyAdapter = historyAdapter;
    }

    private ServiceJobDTO convertToDTO(ServiceJob entity) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setId(entity.getId());
        dto.setJobNo(entity.getJobNo());
        dto.setVehicleId(entity.getVehicleId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setAssignedEmployeeId(entity.getAssignedEmployeeId());
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().name());
        }
        dto.setJobDate(entity.getJobDate());
        dto.setExpectedCompletionDate(entity.getExpectedCompletionDate());
        dto.setActualCompletionDate(entity.getActualCompletionDate());
        dto.setComplaintDescription(entity.getComplaintDescription());
        dto.setNotes(entity.getNotes());
        return dto;
    }

    private ServiceJob convertToEntity(ServiceJobDTO dto) {
        ServiceJob entity = new ServiceJob();
        entity.setId(dto.getId());
        entity.setJobNo(dto.getJobNo());
        entity.setVehicleId(dto.getVehicleId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setAssignedEmployeeId(dto.getAssignedEmployeeId());
        if (dto.getStatus() != null) {
            entity.setStatus(ServiceJob.JobStatus.valueOf(dto.getStatus()));
        }
        entity.setJobDate(dto.getJobDate());
        entity.setExpectedCompletionDate(dto.getExpectedCompletionDate());
        entity.setActualCompletionDate(dto.getActualCompletionDate());
        entity.setComplaintDescription(dto.getComplaintDescription());
        entity.setNotes(dto.getNotes());
        return entity;
    }

    @Override
    public ServiceJobDTO createJob(ServiceJobDTO jobDTO) {
        ServiceJob job = convertToEntity(jobDTO);

        // Auto-generate Job Number (JOB-0001)
        ServiceJob lastJob = repository.findTopByOrderByIdDesc();
        if (lastJob != null && lastJob.getJobNo() != null && lastJob.getJobNo().startsWith("JOB-")) {
            String lastJobNo = lastJob.getJobNo();
            int nextNumber = Integer.parseInt(lastJobNo.replace("JOB-", "")) + 1;
            job.setJobNo(String.format("JOB-%04d", nextNumber));
        } else {
            job.setJobNo("JOB-0001");
        }

        job.setStatus(ServiceJob.JobStatus.PENDING);
        job.setJobDate(LocalDateTime.now());

        ServiceJob savedEntity = repository.save(job);
        return convertToDTO(savedEntity);
    }

    @Override
    public List<ServiceJobDTO> getAllJobs() {
        return repository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceJobDTO getJobById(Long id) {
        ServiceJob entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));
        return convertToDTO(entity);
    }

    @Override
    public ServiceJobDTO getJobByJobNo(String jobNo) {
        ServiceJob entity = repository.findByJobNo(jobNo)
                .orElseThrow(() -> new RuntimeException("Job not found with Job No: " + jobNo));
        return convertToDTO(entity);
    }

    @Override
    public ServiceJobDTO updateJob(Long id, ServiceJobDTO updatedDTO) {
        ServiceJob existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        if (updatedDTO.getComplaintDescription() != null) existing.setComplaintDescription(updatedDTO.getComplaintDescription());
        if (updatedDTO.getAssignedEmployeeId() != null) existing.setAssignedEmployeeId(updatedDTO.getAssignedEmployeeId());
        if (updatedDTO.getExpectedCompletionDate() != null) existing.setExpectedCompletionDate(updatedDTO.getExpectedCompletionDate());
        if (updatedDTO.getNotes() != null) existing.setNotes(updatedDTO.getNotes());

        ServiceJob savedEntity = repository.save(existing);
        return convertToDTO(savedEntity);
    }

    @Override
    @Transactional
    public ServiceJobDTO updateJobStatus(Long id, ServiceJob.JobStatus status) {
        ServiceJob existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        existing.setStatus(status);
        if (status == ServiceJob.JobStatus.COMPLETED) {
            existing.setActualCompletionDate(LocalDateTime.now());
        }

        ServiceJob savedEntity = repository.save(existing);

        // Call Member 06 adapter after successful completion or closure
        if (status == ServiceJob.JobStatus.COMPLETED || status == ServiceJob.JobStatus.CLOSED) {
            try {
                JobCompletionRequest request = buildHistoryRequest(savedEntity);
                historyAdapter.createHistoryFromJobCompletion(request);
                log.info("Service history created for completed job {}", savedEntity.getId());
            } catch (IllegalArgumentException e) {
                log.error("Failed to create service history for job {}: {}", savedEntity.getId(), e.getMessage());
                // History creation failure should not rollback job completion
                // The job status change is valid; history creation is a secondary operation
            }
        }

        return convertToDTO(savedEntity);
    }

    private JobCompletionRequest buildHistoryRequest(ServiceJob job) {
        JobCompletionRequest request = new JobCompletionRequest();
        request.setJobId(job.getId());
        request.setVehicleId(job.getVehicleId());
        request.setServiceDate(job.getActualCompletionDate() != null
                ? job.getActualCompletionDate().toLocalDate()
                : LocalDate.now());

        // Get odometer from vehicle's current mileage
        Integer odometer = 0;
        try {
            var vehicle = vehicleService.getVehicleEntityById(job.getVehicleId());
            if (vehicle != null && vehicle.getCurrentMileage() != null) {
                odometer = vehicle.getCurrentMileage().intValue();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve vehicle mileage for job {}: {}", job.getId(), e.getMessage());
        }
        request.setOdometer(odometer);

        request.setSummary("Job completed: " + (job.getComplaintDescription() != null ? job.getComplaintDescription() : "Service completed"));
        request.setTechnicianId(job.getAssignedEmployeeId());
        // Invoice ID would be populated if Member 05 integration exists
        request.setInvoiceId(null);

        // Calculate total cost from job items and parts
        BigDecimal totalCost = BigDecimal.ZERO;
        // This would require integration with job items/parts services
        // For now, using zero as placeholder
        request.setTotalCost(totalCost);

        // Calculate next service date based on vehicle/service type
        // For now, using null as placeholder - can be enhanced with business rules
        request.setNextServiceDate(null);

        return request;
    }

    @Override
    public void deleteJob(Long id) {
        repository.deleteById(id);
    }
}