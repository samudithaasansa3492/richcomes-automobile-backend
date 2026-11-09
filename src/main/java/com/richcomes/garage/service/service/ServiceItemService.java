package com.richcomes.garage.service.service;

import com.richcomes.garage.service.dto.ServiceItemDTO;
import java.util.List;

public interface ServiceItemService {
    ServiceItemDTO createServiceItem(ServiceItemDTO serviceItemDTO);
    List<ServiceItemDTO> getAllServiceItems();
    ServiceItemDTO getServiceItemById(Long id);
    ServiceItemDTO updateServiceItem(Long id, ServiceItemDTO serviceItemDTO);
    void deleteServiceItem(Long id);
    List<ServiceItemDTO> getItemsByCategoryId(Long categoryId);
}