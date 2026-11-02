
package com.richcomes.garage.inventory.repository;

import com.richcomes.garage.inventory.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository
        extends JpaRepository<StockItem, Long> {

    // Find an item using item code
    Optional<StockItem> findByItemCode(String itemCode);

    // Find an item using barcode
    Optional<StockItem> findByBarcode(String barcode);

    // Check duplicate item code
    boolean existsByItemCode(String itemCode);

    // Check duplicate barcode
    boolean existsByBarcode(String barcode);

    // Get all items belonging to a category
    List<StockItem> findByCategoryId(Long categoryId);

    // Get active stock items
    List<StockItem> findByActiveTrue();

    // Get low-stock items
    List<StockItem>
    findByActiveTrueAndCurrentQuantityLessThanEqual(
            Integer quantity
    );
}
