package com.richcomes.garage.service.repository;

import com.richcomes.garage.service.entity.ServiceJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceJobRepository extends JpaRepository<ServiceJob, Long> {

    Optional<ServiceJob> findByJobNo(String jobNo);


    ServiceJob findTopByOrderByIdDesc();
}

