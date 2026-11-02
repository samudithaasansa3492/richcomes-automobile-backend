package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.repository.ServiceJobRepository;
import com.richcomes.garage.service.service.ServiceJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceJobServiceImpl implements ServiceJobService {

    private final ServiceJobRepository repository;

    @Autowired
    public ServiceJobServiceImpl(ServiceJobRepository repository) {
        this.repository = repository;
    }

    // Helper Method: Entity එක DTO එකකට හැරවීම
    private ServiceJobDTO convertToDTO(ServiceJob entity) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setId(entity.getId());
        dto.setJobNo(entity.getJobNo());
        dto.setVehicleId(entity.getVehicleId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setBranchId(entity.getBranchId());
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

    // Helper Method: DTO එක Entity එකකට හැරවීම
    private ServiceJob convertToEntity(ServiceJobDTO dto) {
        ServiceJob entity = new ServiceJob();
        entity.setId(dto.getId());
        entity.setJobNo(dto.getJobNo());
        entity.setVehicleId(dto.getVehicleId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setBranchId(dto.getBranchId());
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
    public ServiceJobDTO updateJobStatus(Long id, ServiceJob.JobStatus status) {
        ServiceJob existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        existing.setStatus(status);
        if (status == ServiceJob.JobStatus.COMPLETED) {
            existing.setActualCompletionDate(LocalDateTime.now());
        }

        ServiceJob savedEntity = repository.save(existing);
        return convertToDTO(savedEntity);
    }

    @Override
    public void deleteJob(Long id) {
        repository.deleteById(id);
    }
}