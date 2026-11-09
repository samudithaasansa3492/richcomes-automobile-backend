package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.entity.ServiceJobPart;
import com.richcomes.garage.service.repository.ServiceJobPartRepository;
import com.richcomes.garage.service.repository.ServiceJobRepository;
import com.richcomes.garage.service.service.ServiceJobPartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceJobPartServiceImpl implements ServiceJobPartService {

    private final ServiceJobPartRepository repository;
    private final ServiceJobRepository jobRepository;

    @Autowired
    public ServiceJobPartServiceImpl(ServiceJobPartRepository repository, ServiceJobRepository jobRepository) {
        this.repository = repository;
        this.jobRepository = jobRepository;
    }

    private ServiceJobPartDTO convertToDTO(ServiceJobPart entity) {
        ServiceJobPartDTO dto = new ServiceJobPartDTO();
        dto.setId(entity.getId());
        if (entity.getServiceJob() != null) {
            dto.setServiceJobId(entity.getServiceJob().getId());
        }
        dto.setPartId(entity.getPartId());
        dto.setQuantity(entity.getQuantity());
        dto.setUnitPrice(entity.getUnitPrice());
        dto.setTotalAmount(entity.getTotalAmount());
        return dto;
    }

    private ServiceJobPart convertToEntity(ServiceJobPartDTO dto) {
        ServiceJobPart entity = new ServiceJobPart();
        entity.setId(dto.getId());
        if (dto.getServiceJobId() != null) {
            ServiceJob job = jobRepository.findById(dto.getServiceJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found with id: " + dto.getServiceJobId()));
            entity.setServiceJob(job);
        }
        entity.setPartId(dto.getPartId());
        entity.setQuantity(dto.getQuantity());
        entity.setUnitPrice(dto.getUnitPrice());
        entity.setTotalAmount(dto.getTotalAmount());
        return entity;
    }

    @Override
    public ServiceJobPartDTO addJobPart(ServiceJobPartDTO jobPartDTO) {
        ServiceJobPart entity = convertToEntity(jobPartDTO);
        ServiceJobPart saved = repository.save(entity);
        return convertToDTO(saved);
    }

    @Override
    public List<ServiceJobPartDTO> getPartsByJobId(Long jobId) {
        return repository.findByServiceJobId(jobId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void removeJobPart(Long id) {
        repository.deleteById(id);
    }
}