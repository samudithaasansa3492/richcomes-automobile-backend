package com.richcomes.garage.service.controller;

import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import com.richcomes.garage.service.service.ServiceJobPartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-job-parts")
@CrossOrigin(origins = "*")
public class ServiceJobPartController {

    private final ServiceJobPartService service;

    @Autowired
    public ServiceJobPartController(ServiceJobPartService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServiceJobPartDTO> addJobPart(@RequestBody ServiceJobPartDTO jobPartDTO) {
        return ResponseEntity.ok(service.addJobPart(jobPartDTO));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<ServiceJobPartDTO>> getPartsByJobId(@PathVariable Long jobId) {
        return ResponseEntity.ok(service.getPartsByJobId(jobId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeJobPart(@PathVariable Long id) {
        service.removeJobPart(id);
        return ResponseEntity.ok().build();
    }
}