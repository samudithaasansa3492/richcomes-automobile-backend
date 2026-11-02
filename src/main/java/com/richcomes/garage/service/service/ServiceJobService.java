package com.richcomes.garage.service.service;

import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import java.util.List;

public interface ServiceJobService {
    ServiceJobDTO createJob(ServiceJobDTO jobDTO);
    List<ServiceJobDTO> getAllJobs();
    ServiceJobDTO getJobById(Long id);
    ServiceJobDTO getJobByJobNo(String jobNo);
    ServiceJobDTO updateJob(Long id, ServiceJobDTO jobDTO);
    ServiceJobDTO updateJobStatus(Long id, ServiceJob.JobStatus status);
    void deleteJob(Long id);
}