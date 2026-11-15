
package com.richcomes.garage.report.dto;

import java.math.BigDecimal;
import java.util.List;

public class StockReportResponse {

    private ReportMetadata metadata;

    // Current Inventory Snapshot
    private long totalStockItems;
    private long activeStockItems;
    private long totalCategories;

    // Decimal quantity support
    private BigDecimal totalUnitsInStock = BigDecimal.ZERO;

    private long lowStockCount;
    private BigDecimal totalInventoryCostValue;
    private BigDecimal totalInventorySellingValue;
    private BigDecimal potentialGrossProfit;

    // Period Stock Movements
    private long totalMovementsInPeriod;

    // Decimal movement quantities
    private BigDecimal stockInQuantity = BigDecimal.ZERO;
    private BigDecimal stockOutQuantity = BigDecimal.ZERO;
    private BigDecimal damageQuantity = BigDecimal.ZERO;
    private BigDecimal wastageQuantity = BigDecimal.ZERO;
    private BigDecimal adjustmentInQuantity = BigDecimal.ZERO;
    private BigDecimal adjustmentOutQuantity = BigDecimal.ZERO;

    // Period GRN & Suppliers
    private long totalSuppliers;
    private long activeSuppliers;
    private long grnsInPeriod;
    private long confirmedGrnsInPeriod;
    private long draftGrnsInPeriod;
    private long cancelledGrnsInPeriod;
    private BigDecimal confirmedGrnTotalSpend;

    private List<LowStockItemAlert> lowStockItems;
    private List<CategoryStockSummary> categoryBreakdown;
    private List<SupplierSpendSummary> supplierSpendBreakdown;
    private List<ChartSeriesDTO.ChartSegment> movementTypeDistribution;

    public StockReportResponse() {
    }

    // LOW STOCK ALERT
    public static class LowStockItemAlert {

        private Long itemId;
        private String itemCode;
        private String itemName;
        private String categoryName;

        private BigDecimal currentQuantity;
        private BigDecimal reorderLevel;
        private BigDecimal deficit;

        private BigDecimal unitCost;

        public LowStockItemAlert() {
        }

        public LowStockItemAlert(
                Long itemId,
                String itemCode,
                String itemName,
                String categoryName,
                BigDecimal currentQuantity,
                BigDecimal reorderLevel,
                BigDecimal deficit,
                BigDecimal unitCost) {

            this.itemId = itemId;
            this.itemCode = itemCode;
            this.itemName = itemName;
            this.categoryName = categoryName;
            this.currentQuantity = currentQuantity;
            this.reorderLevel = reorderLevel;
            this.deficit = deficit;
            this.unitCost = unitCost;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public BigDecimal getCurrentQuantity() {
            return currentQuantity;
        }

        public void setCurrentQuantity(BigDecimal currentQuantity) {
            this.currentQuantity = currentQuantity;
        }

        public BigDecimal getReorderLevel() {
            return reorderLevel;
        }

        public void setReorderLevel(BigDecimal reorderLevel) {
            this.reorderLevel = reorderLevel;
        }

        public BigDecimal getDeficit() {
            return deficit;
        }

        public void setDeficit(BigDecimal deficit) {
            this.deficit = deficit;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
        }
    }

    // CATEGORY STOCK SUMMARY
    public static class CategoryStockSummary {

        private Long categoryId;
        private String categoryName;
        private long itemCount;

        // Decimal category stock total
        private BigDecimal totalQuantity;

        private BigDecimal costValuation;
        private BigDecimal sellingValuation;

        public CategoryStockSummary() {
        }

        public CategoryStockSummary(
                Long categoryId,
                String categoryName,
                long itemCount,
                BigDecimal totalQuantity,
                BigDecimal costValuation,
                BigDecimal sellingValuation) {

            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.itemCount = itemCount;
            this.totalQuantity = totalQuantity;
            this.costValuation = costValuation;
            this.sellingValuation = sellingValuation;
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public long getItemCount() {
            return itemCount;
        }

        public void setItemCount(long itemCount) {
            this.itemCount = itemCount;
        }

        public BigDecimal getTotalQuantity() {
            return totalQuantity;
        }

        public void setTotalQuantity(BigDecimal totalQuantity) {
            this.totalQuantity = totalQuantity;
        }

        public BigDecimal getCostValuation() {
            return costValuation;
        }

        public void setCostValuation(BigDecimal costValuation) {
            this.costValuation = costValuation;
        }

        public BigDecimal getSellingValuation() {
            return sellingValuation;
        }

        public void setSellingValuation(BigDecimal sellingValuation) {
            this.sellingValuation = sellingValuation;
        }
    }

    // SUPPLIER SPEND SUMMARY
    public static class SupplierSpendSummary {

        private Long supplierId;
        private String supplierName;
        private long confirmedGrnCount;
        private BigDecimal totalSpend;

        public SupplierSpendSummary() {
        }

        public SupplierSpendSummary(
                Long supplierId,
                String supplierName,
                long confirmedGrnCount,
                BigDecimal totalSpend) {

            this.supplierId = supplierId;
            this.supplierName = supplierName;
            this.confirmedGrnCount = confirmedGrnCount;
            this.totalSpend = totalSpend;
        }

        public Long getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(Long supplierId) {
            this.supplierId = supplierId;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public long getConfirmedGrnCount() {
            return confirmedGrnCount;
        }

        public void setConfirmedGrnCount(long confirmedGrnCount) {
            this.confirmedGrnCount = confirmedGrnCount;
        }

        public BigDecimal getTotalSpend() {
            return totalSpend;
        }

        public void setTotalSpend(BigDecimal totalSpend) {
            this.totalSpend = totalSpend;
        }
    }

    // MAIN REPORT GETTERS AND SETTERS

    public ReportMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ReportMetadata metadata) {
        this.metadata = metadata;
    }

    public long getTotalStockItems() {
        return totalStockItems;
    }

    public void setTotalStockItems(long totalStockItems) {
        this.totalStockItems = totalStockItems;
    }

    public long getActiveStockItems() {
        return activeStockItems;
    }

    public void setActiveStockItems(long activeStockItems) {
        this.activeStockItems = activeStockItems;
    }

    public long getTotalCategories() {
        return totalCategories;
    }

    public void setTotalCategories(long totalCategories) {
        this.totalCategories = totalCategories;
    }

    public BigDecimal getTotalUnitsInStock() {
        return totalUnitsInStock;
    }

    public void setTotalUnitsInStock(BigDecimal totalUnitsInStock) {
        this.totalUnitsInStock = totalUnitsInStock;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(long lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public BigDecimal getTotalInventoryCostValue() {
        return totalInventoryCostValue;
    }

    public void setTotalInventoryCostValue(
            BigDecimal totalInventoryCostValue) {
        this.totalInventoryCostValue = totalInventoryCostValue;
    }

    public BigDecimal getTotalInventorySellingValue() {
        return totalInventorySellingValue;
    }

    public void setTotalInventorySellingValue(
            BigDecimal totalInventorySellingValue) {
        this.totalInventorySellingValue = totalInventorySellingValue;
    }

    public BigDecimal getPotentialGrossProfit() {
        return potentialGrossProfit;
    }

    public void setPotentialGrossProfit(
            BigDecimal potentialGrossProfit) {
        this.potentialGrossProfit = potentialGrossProfit;
    }

    public long getTotalMovementsInPeriod() {
        return totalMovementsInPeriod;
    }

    public void setTotalMovementsInPeriod(
            long totalMovementsInPeriod) {
        this.totalMovementsInPeriod = totalMovementsInPeriod;
    }

    public BigDecimal getStockInQuantity() {
        return stockInQuantity;
    }

    public void setStockInQuantity(BigDecimal stockInQuantity) {
        this.stockInQuantity = stockInQuantity;
    }

    public BigDecimal getStockOutQuantity() {
        return stockOutQuantity;
    }

    public void setStockOutQuantity(BigDecimal stockOutQuantity) {
        this.stockOutQuantity = stockOutQuantity;
    }

    public BigDecimal getDamageQuantity() {
        return damageQuantity;
    }

    public void setDamageQuantity(BigDecimal damageQuantity) {
        this.damageQuantity = damageQuantity;
    }

    public BigDecimal getWastageQuantity() {
        return wastageQuantity;
    }

    public void setWastageQuantity(BigDecimal wastageQuantity) {
        this.wastageQuantity = wastageQuantity;
    }

    public BigDecimal getAdjustmentInQuantity() {
        return adjustmentInQuantity;
    }

    public void setAdjustmentInQuantity(
            BigDecimal adjustmentInQuantity) {
        this.adjustmentInQuantity = adjustmentInQuantity;
    }

    public BigDecimal getAdjustmentOutQuantity() {
        return adjustmentOutQuantity;
    }

    public void setAdjustmentOutQuantity(
            BigDecimal adjustmentOutQuantity) {
        this.adjustmentOutQuantity = adjustmentOutQuantity;
    }

    public long getTotalSuppliers() {
        return totalSuppliers;
    }

    public void setTotalSuppliers(long totalSuppliers) {
        this.totalSuppliers = totalSuppliers;
    }

    public long getActiveSuppliers() {
        return activeSuppliers;
    }

    public void setActiveSuppliers(long activeSuppliers) {
        this.activeSuppliers = activeSuppliers;
    }

    public long getGrnsInPeriod() {
        return grnsInPeriod;
    }

    public void setGrnsInPeriod(long grnsInPeriod) {
        this.grnsInPeriod = grnsInPeriod;
    }

    public long getConfirmedGrnsInPeriod() {
        return confirmedGrnsInPeriod;
    }

    public void setConfirmedGrnsInPeriod(
            long confirmedGrnsInPeriod) {
        this.confirmedGrnsInPeriod = confirmedGrnsInPeriod;
    }

    public long getDraftGrnsInPeriod() {
        return draftGrnsInPeriod;
    }

    public void setDraftGrnsInPeriod(long draftGrnsInPeriod) {
        this.draftGrnsInPeriod = draftGrnsInPeriod;
    }

    public long getCancelledGrnsInPeriod() {
        return cancelledGrnsInPeriod;
    }

    public void setCancelledGrnsInPeriod(
            long cancelledGrnsInPeriod) {
        this.cancelledGrnsInPeriod = cancelledGrnsInPeriod;
    }

    public BigDecimal getConfirmedGrnTotalSpend() {
        return confirmedGrnTotalSpend;
    }

    public void setConfirmedGrnTotalSpend(
            BigDecimal confirmedGrnTotalSpend) {
        this.confirmedGrnTotalSpend = confirmedGrnTotalSpend;
    }

    public List<LowStockItemAlert> getLowStockItems() {
        return lowStockItems;
    }

    public void setLowStockItems(
            List<LowStockItemAlert> lowStockItems) {
        this.lowStockItems = lowStockItems;
    }

    public List<CategoryStockSummary> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    public void setCategoryBreakdown(
            List<CategoryStockSummary> categoryBreakdown) {
        this.categoryBreakdown = categoryBreakdown;
    }

    public List<SupplierSpendSummary> getSupplierSpendBreakdown() {
        return supplierSpendBreakdown;
    }

    public void setSupplierSpendBreakdown(
            List<SupplierSpendSummary> supplierSpendBreakdown) {
        this.supplierSpendBreakdown = supplierSpendBreakdown;
    }

    public List<ChartSeriesDTO.ChartSegment>
    getMovementTypeDistribution() {
        return movementTypeDistribution;
    }

    public void setMovementTypeDistribution(
            List<ChartSeriesDTO.ChartSegment>
                    movementTypeDistribution) {
        this.movementTypeDistribution = movementTypeDistribution;
    }
}
