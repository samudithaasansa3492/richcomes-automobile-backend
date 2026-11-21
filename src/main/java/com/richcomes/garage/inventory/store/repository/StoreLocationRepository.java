
package com.richcomes.garage.inventory.store.repository;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreLocationRepository
        extends JpaRepository<StoreLocation, Long> {

    // Location dropdown ekata active stores witharai
    List<StoreLocation> findByActiveTrueOrderByStoreNameAsc();

    // Duplicate store codes prevent karanna
    boolean existsByStoreCodeIgnoreCase(String storeCode);

    // Store code eken location hoyanna
    Optional<StoreLocation> findByStoreCodeIgnoreCase(String storeCode);
}
