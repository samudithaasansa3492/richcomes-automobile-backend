
package com.richcomes.garage.inventory.store.entity;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "store_stock_balances",
        schema = "garage",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_store_stock_item",
                        columnNames = {"store_location_id", "stock_item_id"}
                )
        }
)
public class StoreStockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_location_id", nullable = false)
    private StoreLocation storeLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(
            name = "quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantity = BigDecimal.ZERO;

    public StoreStockBalance() {
    }

    public Long getId() {
        return id;
    }

    public StoreLocation getStoreLocation() {
        return storeLocation;
    }

    public void setStoreLocation(StoreLocation storeLocation) {
        this.storeLocation = storeLocation;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public void setStockItem(StockItem stockItem) {
        this.stockItem = stockItem;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
