package com.richcomes.garage.service.repository;

import com.richcomes.garage.service.entity.ServiceJobItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceJobItemRepository extends JpaRepository<ServiceJobItem, Long> {

    List<ServiceJobItem> findByServiceJobId(Long serviceJobId);
}