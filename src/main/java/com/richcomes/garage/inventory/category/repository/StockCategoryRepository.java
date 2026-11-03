
package com.richcomes.garage.inventory.category.repository;

import com.richcomes.garage.inventory.category.entity.StockCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockCategoryRepository
        extends JpaRepository<StockCategory, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<StockCategory> findByNameIgnoreCase(String name);
}
