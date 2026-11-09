package com.richcomes.garage.service.controller;

import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.service.ServiceJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-jobs")
@CrossOrigin(origins = "*")
public class ServiceJobController {

    private final ServiceJobService service;

    @Autowired
    public ServiceJobController(ServiceJobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServiceJobDTO> createJob(@RequestBody ServiceJobDTO jobDTO) {
        return ResponseEntity.ok(service.createJob(jobDTO));
    }

    @GetMapping
    public ResponseEntity<List<ServiceJobDTO>> getAllJobs() {
        return ResponseEntity.ok(service.getAllJobs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceJobDTO> getJobById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getJobById(id));
    }

    @GetMapping("/job-no/{jobNo}")
    public ResponseEntity<ServiceJobDTO> getJobByJobNo(@PathVariable String jobNo) {
        return ResponseEntity.ok(service.getJobByJobNo(jobNo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceJobDTO> updateJob(@PathVariable Long id, @RequestBody ServiceJobDTO jobDTO) {
        return ResponseEntity.ok(service.updateJob(id, jobDTO));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceJobDTO> updateJobStatus(@PathVariable Long id, @RequestParam ServiceJob.JobStatus status) {
        return ResponseEntity.ok(service.updateJobStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        service.deleteJob(id);
        return ResponseEntity.ok().build();
    }
}