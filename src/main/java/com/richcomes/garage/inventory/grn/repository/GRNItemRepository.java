
package com.richcomes.garage.inventory.grn.repository;

import com.richcomes.garage.inventory.grn.entity.GRNItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GRNItemRepository
        extends JpaRepository<GRNItem, Long> {

    List<GRNItem> findByGrnId(Long grnId);

    List<GRNItem> findByStockItemId(Long stockItemId);
}
