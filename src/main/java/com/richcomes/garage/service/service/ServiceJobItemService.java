package com.richcomes.garage.service.service;

import com.richcomes.garage.service.dto.ServiceJobItemDTO;
import java.util.List;

public interface ServiceJobItemService {
    ServiceJobItemDTO addJobItem(ServiceJobItemDTO jobItemDTO);
    List<ServiceJobItemDTO> getItemsByJobId(Long jobId);
    void removeJobItem(Long id);
}