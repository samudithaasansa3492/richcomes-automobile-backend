package com.richcomes.garage.report.dto;

import java.math.BigDecimal;
import java.util.List;

public class FinancialReportResponse {

    private ReportMetadata metadata;

    // Service History Recorded Revenue
    private BigDecimal recordedServiceRevenue;
    private long recordedServiceCount;
    private BigDecimal averageServiceTicketValue;

    // Billable Breakdown from Active/Completed Jobs in Period
    private BigDecimal jobLabourRevenue;
    private BigDecimal jobPartsRevenue;
    private BigDecimal jobBillableTotal;

    // Procurement & Costs
    private BigDecimal confirmedGrnProcurementSpend;
    private BigDecimal draftGrnPendingValue;
    private BigDecimal netOperatingCashEstimate;

    // Customer & Payroll Baselines
    private BigDecimal totalCustomerCreditLimitPool;
    private long activeEmployeeCount;
    private BigDecimal activeStaffDailyRateTotal;

    private String billingIntegrationNote;

    private List<ChartSeriesDTO.TimeSeriesPoint> financialTrend;
    private List<ChartSeriesDTO.ChartSegment> revenueComposition;

    public FinancialReportResponse() {
    }

    public ReportMetadata getMetadata() { return metadata; }
    public void setMetadata(ReportMetadata metadata) { this.metadata = metadata; }
    public BigDecimal getRecordedServiceRevenue() { return recordedServiceRevenue; }
    public void setRecordedServiceRevenue(BigDecimal recordedServiceRevenue) { this.recordedServiceRevenue = recordedServiceRevenue; }
    public long getRecordedServiceCount() { return recordedServiceCount; }
    public void setRecordedServiceCount(long recordedServiceCount) { this.recordedServiceCount = recordedServiceCount; }
    public BigDecimal getAverageServiceTicketValue() { return averageServiceTicketValue; }
    public void setAverageServiceTicketValue(BigDecimal averageServiceTicketValue) { this.averageServiceTicketValue = averageServiceTicketValue; }
    public BigDecimal getJobLabourRevenue() { return jobLabourRevenue; }
    public void setJobLabourRevenue(BigDecimal jobLabourRevenue) { this.jobLabourRevenue = jobLabourRevenue; }
    public BigDecimal getJobPartsRevenue() { return jobPartsRevenue; }
    public void setJobPartsRevenue(BigDecimal jobPartsRevenue) { this.jobPartsRevenue = jobPartsRevenue; }
    public BigDecimal getJobBillableTotal() { return jobBillableTotal; }
    public void setJobBillableTotal(BigDecimal jobBillableTotal) { this.jobBillableTotal = jobBillableTotal; }
    public BigDecimal getConfirmedGrnProcurementSpend() { return confirmedGrnProcurementSpend; }
    public void setConfirmedGrnProcurementSpend(BigDecimal confirmedGrnProcurementSpend) { this.confirmedGrnProcurementSpend = confirmedGrnProcurementSpend; }
    public BigDecimal getDraftGrnPendingValue() { return draftGrnPendingValue; }
    public void setDraftGrnPendingValue(BigDecimal draftGrnPendingValue) { this.draftGrnPendingValue = draftGrnPendingValue; }
    public BigDecimal getNetOperatingCashEstimate() { return netOperatingCashEstimate; }
    public void setNetOperatingCashEstimate(BigDecimal netOperatingCashEstimate) { this.netOperatingCashEstimate = netOperatingCashEstimate; }
    public BigDecimal getTotalCustomerCreditLimitPool() { return totalCustomerCreditLimitPool; }
    public void setTotalCustomerCreditLimitPool(BigDecimal totalCustomerCreditLimitPool) { this.totalCustomerCreditLimitPool = totalCustomerCreditLimitPool; }
    public long getActiveEmployeeCount() { return activeEmployeeCount; }
    public void setActiveEmployeeCount(long activeEmployeeCount) { this.activeEmployeeCount = activeEmployeeCount; }
    public BigDecimal getActiveStaffDailyRateTotal() { return activeStaffDailyRateTotal; }
    public void setActiveStaffDailyRateTotal(BigDecimal activeStaffDailyRateTotal) { this.activeStaffDailyRateTotal = activeStaffDailyRateTotal; }
    public String getBillingIntegrationNote() { return billingIntegrationNote; }
    public void setBillingIntegrationNote(String billingIntegrationNote) { this.billingIntegrationNote = billingIntegrationNote; }
    public List<ChartSeriesDTO.TimeSeriesPoint> getFinancialTrend() { return financialTrend; }
    public void setFinancialTrend(List<ChartSeriesDTO.TimeSeriesPoint> financialTrend) { this.financialTrend = financialTrend; }
    public List<ChartSeriesDTO.ChartSegment> getRevenueComposition() { return revenueComposition; }
    public void setRevenueComposition(List<ChartSeriesDTO.ChartSegment> revenueComposition) { this.revenueComposition = revenueComposition; }
}