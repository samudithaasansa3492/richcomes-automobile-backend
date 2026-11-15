package com.richcomes.garage.service.controller;

import com.richcomes.garage.service.dto.ServiceItemDTO;
import com.richcomes.garage.service.service.ServiceItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-items")
@CrossOrigin(origins = "*")
public class ServiceItemController {

    private final ServiceItemService service;

    @Autowired
    public ServiceItemController(ServiceItemService service) {
        this.service = service;
    }


    @PostMapping
    public ResponseEntity<ServiceItemDTO> createServiceItem(@RequestBody ServiceItemDTO serviceItemDTO) {
        return ResponseEntity.ok(service.createServiceItem(serviceItemDTO));
    }


    @GetMapping
    public ResponseEntity<List<ServiceItemDTO>> getAllServiceItems() {
        return ResponseEntity.ok(service.getAllServiceItems());
    }


    @GetMapping("/{id}")
    public ResponseEntity<ServiceItemDTO> getServiceItemById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getServiceItemById(id));
    }


    @PutMapping("/{id}")
    public ResponseEntity<ServiceItemDTO> updateServiceItem(@PathVariable Long id, @RequestBody ServiceItemDTO serviceItemDTO) {
        return ResponseEntity.ok(service.updateServiceItem(id, serviceItemDTO));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServiceItem(@PathVariable Long id) {
        service.deleteServiceItem(id);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ServiceItemDTO>> getItemsByCategoryId(@PathVariable Long categoryId) {
        return ResponseEntity.ok(service.getItemsByCategoryId(categoryId));
    }
}