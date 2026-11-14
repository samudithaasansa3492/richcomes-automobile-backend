
package com.richcomes.garage.inventory.stockitem.repository;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
//import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;


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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id = :id")
    Optional<StockItem> findByIdForUpdate(@Param("id") Long id);
}
