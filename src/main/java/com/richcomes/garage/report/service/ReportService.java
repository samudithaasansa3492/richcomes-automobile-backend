
package com.richcomes.garage.report.service;

import com.richcomes.garage.report.dto.ComprehensiveReportResponse;
import com.richcomes.garage.report.dto.DashboardReportResponse;
import com.richcomes.garage.report.dto.FinancialReportResponse;
import com.richcomes.garage.report.dto.OperationalReportResponse;
import com.richcomes.garage.report.dto.PerformanceReportResponse;
import com.richcomes.garage.report.dto.StockReportResponse;
import com.richcomes.garage.report.enums.ReportPeriod;

import java.time.LocalDate;

public interface ReportService {

    DashboardReportResponse getDashboardReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    );

    OperationalReportResponse getOperationalReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    );

    StockReportResponse getStockReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    );

    FinancialReportResponse getFinancialReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    );

    PerformanceReportResponse getPerformanceReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate,
            Long technicianId,
            Long actorUserId
    );

    ComprehensiveReportResponse getComprehensiveReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    );
}