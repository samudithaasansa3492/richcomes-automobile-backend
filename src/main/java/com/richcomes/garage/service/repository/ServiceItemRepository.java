package com.richcomes.garage.service.repository;

import com.richcomes.garage.service.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    // යම්කිසි Category එකකට අදාළ සියලුම සේවාවන් සොයාගැනීමට
    List<ServiceItem> findByCategoryId(Long categoryId);
}