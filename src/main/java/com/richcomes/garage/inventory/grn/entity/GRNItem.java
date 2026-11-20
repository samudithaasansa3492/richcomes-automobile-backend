
package com.richcomes.garage.inventory.grn.entity;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grn_items", schema = "garage")
public class GRNItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grn_id", nullable = false)
    private GRN grn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(
            name = "received_quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal receivedQuantity;

    @Column(
            name = "unit_cost",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal unitCost;

    // One GRN item can be allocated to multiple stores.
    @OneToMany(
            mappedBy = "grnItem",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<GRNItemAllocation> allocations =
            new ArrayList<>();

    public GRNItem() {
    }

    public void addAllocation(GRNItemAllocation allocation) {

        if (allocation == null) {
            throw new IllegalArgumentException(
                    "GRN allocation cannot be null"
            );
        }

        allocations.add(allocation);
        allocation.setGrnItem(this);
    }

    public Long getId() {
        return id;
    }

    public GRN getGrn() {
        return grn;
    }

    public void setGrn(GRN grn) {
        this.grn = grn;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public void setStockItem(StockItem stockItem) {
        this.stockItem = stockItem;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(BigDecimal receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public List<GRNItemAllocation> getAllocations() {
        return allocations;
    }
}
