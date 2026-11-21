
package com.richcomes.garage.inventory.grn.entity;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "grn_item_allocations",
        schema = "garage",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_grn_item_store",
                        columnNames = {"grn_item_id", "store_location_id"}
                )
        }
)
public class GRNItemAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grn_item_id", nullable = false)
    private GRNItem grnItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_location_id", nullable = false)
    private StoreLocation storeLocation;

    @Column(
            name = "allocated_quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal allocatedQuantity = BigDecimal.ZERO;

    public GRNItemAllocation() {
    }

    public Long getId() {
        return id;
    }

    public GRNItem getGrnItem() {
        return grnItem;
    }

    public void setGrnItem(GRNItem grnItem) {
        this.grnItem = grnItem;
    }

    public StoreLocation getStoreLocation() {
        return storeLocation;
    }

    public void setStoreLocation(StoreLocation storeLocation) {
        this.storeLocation = storeLocation;
    }

    public BigDecimal getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public void setAllocatedQuantity(BigDecimal allocatedQuantity) {
        this.allocatedQuantity = allocatedQuantity;
    }
}
