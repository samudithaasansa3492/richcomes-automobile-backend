package com.richcomes.garage.report.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardReportResponse {

    private ReportMetadata metadata;

    // Executive KPI Cards
    private BigDecimal totalRevenue;
    private BigDecimal totalProcurementSpend;
    private BigDecimal netOperatingEstimate;
    private BigDecimal inventoryValuation;

    private long totalJobs;
    private long completedJobs;
    private BigDecimal jobCompletionRate;
    private long totalAppointments;
    private long pendingAppointments;

    private long activeCustomers;
    private long activeVehicles;
    private long lowStockAlerts;
    private long dueReminders;
    private long failedNotifications;

    // Dashboard Charts (Ready for React Recharts / Chart.js)
    private List<ChartSeriesDTO.TimeSeriesPoint> revenueAndOperationsTrend;
    private List<ChartSeriesDTO.ChartSegment> jobStatusBreakdown;
    private List<ChartSeriesDTO.ChartSegment> appointmentStatusBreakdown;
    private List<ChartSeriesDTO.ChartSegment> stockValueByCategory;
    private List<ChartSeriesDTO.ChartSegment> revenueComposition;
    private List<ChartSeriesDTO.ChartSegment> notificationStatusDistribution;
    private List<PerformanceReportResponse.TechnicianPerformanceItem> topTechnicians;

    public DashboardReportResponse() {
    }

    public ReportMetadata getMetadata() { return metadata; }
    public void setMetadata(ReportMetadata metadata) { this.metadata = metadata; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getTotalProcurementSpend() { return totalProcurementSpend; }
    public void setTotalProcurementSpend(BigDecimal totalProcurementSpend) { this.totalProcurementSpend = totalProcurementSpend; }
    public BigDecimal getNetOperatingEstimate() { return netOperatingEstimate; }
    public void setNetOperatingEstimate(BigDecimal netOperatingEstimate) { this.netOperatingEstimate = netOperatingEstimate; }
    public BigDecimal getInventoryValuation() { return inventoryValuation; }
    public void setInventoryValuation(BigDecimal inventoryValuation) { this.inventoryValuation = inventoryValuation; }
    public long getTotalJobs() { return totalJobs; }
    public void setTotalJobs(long totalJobs) { this.totalJobs = totalJobs; }
    public long getCompletedJobs() { return completedJobs; }
    public void setCompletedJobs(long completedJobs) { this.completedJobs = completedJobs; }
    public BigDecimal getJobCompletionRate() { return jobCompletionRate; }
    public void setJobCompletionRate(BigDecimal jobCompletionRate) { this.jobCompletionRate = jobCompletionRate; }
    public long getTotalAppointments() { return totalAppointments; }
    public void setTotalAppointments(long totalAppointments) { this.totalAppointments = totalAppointments; }
    public long getPendingAppointments() { return pendingAppointments; }
    public void setPendingAppointments(long pendingAppointments) { this.pendingAppointments = pendingAppointments; }
    public long getActiveCustomers() { return activeCustomers; }
    public void setActiveCustomers(long activeCustomers) { this.activeCustomers = activeCustomers; }
    public long getActiveVehicles() { return activeVehicles; }
    public void setActiveVehicles(long activeVehicles) { this.activeVehicles = activeVehicles; }
    public long getLowStockAlerts() { return lowStockAlerts; }
    public void setLowStockAlerts(long lowStockAlerts) { this.lowStockAlerts = lowStockAlerts; }
    public long getDueReminders() { return dueReminders; }
    public void setDueReminders(long dueReminders) { this.dueReminders = dueReminders; }
    public long getFailedNotifications() { return failedNotifications; }
    public void setFailedNotifications(long failedNotifications) { this.failedNotifications = failedNotifications; }
    public List<ChartSeriesDTO.TimeSeriesPoint> getRevenueAndOperationsTrend() { return revenueAndOperationsTrend; }
    public void setRevenueAndOperationsTrend(List<ChartSeriesDTO.TimeSeriesPoint> revenueAndOperationsTrend) { this.revenueAndOperationsTrend = revenueAndOperationsTrend; }
    public List<ChartSeriesDTO.ChartSegment> getJobStatusBreakdown() { return jobStatusBreakdown; }
    public void setJobStatusBreakdown(List<ChartSeriesDTO.ChartSegment> jobStatusBreakdown) { this.jobStatusBreakdown = jobStatusBreakdown; }
    public List<ChartSeriesDTO.ChartSegment> getAppointmentStatusBreakdown() { return appointmentStatusBreakdown; }
    public void setAppointmentStatusBreakdown(List<ChartSeriesDTO.ChartSegment> appointmentStatusBreakdown) { this.appointmentStatusBreakdown = appointmentStatusBreakdown; }
    public List<ChartSeriesDTO.ChartSegment> getStockValueByCategory() { return stockValueByCategory; }
    public void setStockValueByCategory(List<ChartSeriesDTO.ChartSegment> stockValueByCategory) { this.stockValueByCategory = stockValueByCategory; }
    public List<ChartSeriesDTO.ChartSegment> getRevenueComposition() { return revenueComposition; }
    public void setRevenueComposition(List<ChartSeriesDTO.ChartSegment> revenueComposition) { this.revenueComposition = revenueComposition; }
    public List<ChartSeriesDTO.ChartSegment> getNotificationStatusDistribution() {
    return notificationStatusDistribution;
    }

    public void setNotificationStatusDistribution(
            List<ChartSeriesDTO.ChartSegment> notificationStatusDistribution) {
        this.notificationStatusDistribution = notificationStatusDistribution;
    }
    public List<PerformanceReportResponse.TechnicianPerformanceItem> getTopTechnicians() { return topTechnicians; }
    public void setTopTechnicians(List<PerformanceReportResponse.TechnicianPerformanceItem> topTechnicians) { this.topTechnicians = topTechnicians; }
}