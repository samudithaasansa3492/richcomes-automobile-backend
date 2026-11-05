package com.richcomes.garage.history.repository;

import com.richcomes.garage.history.entity.ServiceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceHistoryRepository extends JpaRepository<ServiceHistory, Long> {

    List<ServiceHistory> findByVehicleIdOrderByServiceDateDesc(Long vehicleId);

    Optional<ServiceHistory> findByJobId(Long jobId);

    List<ServiceHistory> findByTechnicianIdOrderByServiceDateDesc(Long technicianId);

    Page<ServiceHistory> findByServiceDateBetween(
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    boolean existsByJobId(Long jobId);
}
