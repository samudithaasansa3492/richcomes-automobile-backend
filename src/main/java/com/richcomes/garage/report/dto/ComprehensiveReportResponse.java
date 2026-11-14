package com.richcomes.garage.report.dto;

public class ComprehensiveReportResponse {

    private ReportMetadata metadata;
    private DashboardReportResponse dashboard;
    private OperationalReportResponse operational;
    private StockReportResponse stock;
    private FinancialReportResponse financial;
    private PerformanceReportResponse performance;

    public ComprehensiveReportResponse() {
    }

    public ReportMetadata getMetadata() { return metadata; }
    public void setMetadata(ReportMetadata metadata) { this.metadata = metadata; }
    public DashboardReportResponse getDashboard() { return dashboard; }
    public void setDashboard(DashboardReportResponse dashboard) { this.dashboard = dashboard; }
    public OperationalReportResponse getOperational() { return operational; }
    public void setOperational(OperationalReportResponse operational) { this.operational = operational; }
    public StockReportResponse getStock() { return stock; }
    public void setStock(StockReportResponse stock) { this.stock = stock; }
    public FinancialReportResponse getFinancial() { return financial; }
    public void setFinancial(FinancialReportResponse financial) { this.financial = financial; }
    public PerformanceReportResponse getPerformance() { return performance; }
    public void setPerformance(PerformanceReportResponse performance) { this.performance = performance; }
}