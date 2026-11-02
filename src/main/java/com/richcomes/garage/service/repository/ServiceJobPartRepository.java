package com.richcomes.garage.service.repository;

import com.richcomes.garage.service.entity.ServiceJobPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceJobPartRepository extends JpaRepository<ServiceJobPart, Long> {
    // යම්කිසි Job එකකට අදාළව භාවිත කර ඇති සියලුම අමතර කොටස් සොයාගැනීමට
    List<ServiceJobPart> findByServiceJobId(Long serviceJobId);
}