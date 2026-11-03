
package com.richcomes.garage.inventory.supplier.repository;

import com.richcomes.garage.inventory.supplier.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository
        extends JpaRepository<Supplier, Long> {

    boolean existsByTinNumber(String tinNumber);

    Optional<Supplier> findByTinNumber(String tinNumber);

    boolean existsBySupplierNameIgnoreCase(String supplierName);
}
