package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.service.dto.ServiceJobItemDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.entity.ServiceItem;
import com.richcomes.garage.service.entity.ServiceJobItem;
import com.richcomes.garage.service.repository.ServiceJobItemRepository;
import com.richcomes.garage.service.repository.ServiceJobRepository;
import com.richcomes.garage.service.repository.ServiceItemRepository;
import com.richcomes.garage.service.service.ServiceJobItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceJobItemServiceImpl implements ServiceJobItemService {

    private final ServiceJobItemRepository repository;
    private final ServiceJobRepository jobRepository;
    private final ServiceItemRepository itemRepository;

    @Autowired
    public ServiceJobItemServiceImpl(ServiceJobItemRepository repository, ServiceJobRepository jobRepository, ServiceItemRepository itemRepository) {
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
    }

    private ServiceJobItemDTO convertToDTO(ServiceJobItem entity) {
        ServiceJobItemDTO dto = new ServiceJobItemDTO();
        dto.setId(entity.getId());
        if (entity.getServiceJob() != null) {
            dto.setServiceJobId(entity.getServiceJob().getId());
        }
        if (entity.getServiceItem() != null) {
            dto.setServiceItemId(entity.getServiceItem().getId());
        }
        dto.setLabourHours(entity.getLabourHours());
        dto.setLabourRate(entity.getLabourRate());
        dto.setTotalAmount(entity.getTotalAmount());
        return dto;
    }

    private ServiceJobItem convertToEntity(ServiceJobItemDTO dto) {
        ServiceJobItem entity = new ServiceJobItem();
        entity.setId(dto.getId());
        if (dto.getServiceJobId() != null) {
            ServiceJob job = jobRepository.findById(dto.getServiceJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found with id: " + dto.getServiceJobId()));
            entity.setServiceJob(job);
        }
        if (dto.getServiceItemId() != null) {
            ServiceItem item = itemRepository.findById(dto.getServiceItemId())
                    .orElseThrow(() -> new RuntimeException("Service Item not found with id: " + dto.getServiceItemId()));
            entity.setServiceItem(item);
        }
        entity.setLabourHours(dto.getLabourHours());
        entity.setLabourRate(dto.getLabourRate());
        entity.setTotalAmount(dto.getTotalAmount());
        return entity;
    }

    @Override
    public ServiceJobItemDTO addJobItem(ServiceJobItemDTO jobItemDTO) {
        ServiceJobItem entity = convertToEntity(jobItemDTO);
        ServiceJobItem saved = repository.save(entity);
        return convertToDTO(saved);
    }

    @Override
    public List<ServiceJobItemDTO> getItemsByJobId(Long jobId) {
        return repository.findByServiceJobId(jobId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void removeJobItem(Long id) {
        repository.deleteById(id);
    }
}