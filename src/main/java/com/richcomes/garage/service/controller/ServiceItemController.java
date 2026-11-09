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

    // අලුත් Service Item එකක් සෑදීමට
    @PostMapping
    public ResponseEntity<ServiceItemDTO> createServiceItem(@RequestBody ServiceItemDTO serviceItemDTO) {
        return ResponseEntity.ok(service.createServiceItem(serviceItemDTO));
    }

    // සියලුම Service Items ලබාගැනීමට
    @GetMapping
    public ResponseEntity<List<ServiceItemDTO>> getAllServiceItems() {
        return ResponseEntity.ok(service.getAllServiceItems());
    }

    // ID එක මගින් නිශ්චිත Service Item එකක් සෙවීමට
    @GetMapping("/{id}")
    public ResponseEntity<ServiceItemDTO> getServiceItemById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getServiceItemById(id));
    }

    // Service Item එකක් යාවත්කාලීන කිරීමට
    @PutMapping("/{id}")
    public ResponseEntity<ServiceItemDTO> updateServiceItem(@PathVariable Long id, @RequestBody ServiceItemDTO serviceItemDTO) {
        return ResponseEntity.ok(service.updateServiceItem(id, serviceItemDTO));
    }

    // Service Item එකක් මකා දැමීමට
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServiceItem(@PathVariable Long id) {
        service.deleteServiceItem(id);
        return ResponseEntity.ok().build();
    }

    // යම්කිසි Category එකකට අදාළ Service Items පමණක් ලබාගැනීමට
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ServiceItemDTO>> getItemsByCategoryId(@PathVariable Long categoryId) {
        return ResponseEntity.ok(service.getItemsByCategoryId(categoryId));
    }
}