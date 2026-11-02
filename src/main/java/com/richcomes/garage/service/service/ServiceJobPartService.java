package com.richcomes.garage.service.service;

import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import java.util.List;

public interface ServiceJobPartService {
    ServiceJobPartDTO addJobPart(ServiceJobPartDTO jobPartDTO);
    List<ServiceJobPartDTO> getPartsByJobId(Long jobId);
    void removeJobPart(Long id);
}