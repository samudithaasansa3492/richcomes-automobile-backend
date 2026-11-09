package com.richcomes.garage.service.controller;

import com.richcomes.garage.service.dto.ServiceJobItemDTO;
import com.richcomes.garage.service.service.ServiceJobItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-job-items")
@CrossOrigin(origins = "*")
public class ServiceJobItemController {

    private final ServiceJobItemService service;

    @Autowired
    public ServiceJobItemController(ServiceJobItemService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServiceJobItemDTO> addJobItem(@RequestBody ServiceJobItemDTO jobItemDTO) {
        return ResponseEntity.ok(service.addJobItem(jobItemDTO));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<ServiceJobItemDTO>> getItemsByJobId(@PathVariable Long jobId) {
        return ResponseEntity.ok(service.getItemsByJobId(jobId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeJobItem(@PathVariable Long id) {
        service.removeJobItem(id);
        return ResponseEntity.ok().build();
    }
}