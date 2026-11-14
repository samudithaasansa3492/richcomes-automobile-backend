package com.richcomes.garage.report.controller;

import com.richcomes.garage.report.dto.ComprehensiveReportResponse;
import com.richcomes.garage.report.dto.DashboardReportResponse;
import com.richcomes.garage.report.dto.FinancialReportResponse;
import com.richcomes.garage.report.dto.OperationalReportResponse;
import com.richcomes.garage.report.dto.PerformanceReportResponse;
import com.richcomes.garage.report.dto.StockReportResponse;
import com.richcomes.garage.report.enums.ReportPeriod;
import com.richcomes.garage.report.enums.ReportType;
import com.richcomes.garage.report.service.ReportPdfService;
import com.richcomes.garage.report.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;

    public ReportController(
            ReportService reportService,
            ReportPdfService reportPdfService
    ) {
        this.reportService = reportService;
        this.reportPdfService = reportPdfService;
    }

    // =====================================================
    // DASHBOARD VISUALIZATION & ANALYTICS (JSON)
    // =====================================================

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardReportResponse> getDashboardReport(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                reportService.getDashboardReport(period, referenceDate, startDate, endDate)
        );
    }

    @GetMapping("/operational")
    public ResponseEntity<OperationalReportResponse> getOperationalReport(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                reportService.getOperationalReport(period, referenceDate, startDate, endDate)
        );
    }

    @GetMapping("/stock")
    public ResponseEntity<StockReportResponse> getStockReport(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                reportService.getStockReport(period, referenceDate, startDate, endDate)
        );
    }

    @GetMapping("/financial")
    public ResponseEntity<FinancialReportResponse> getFinancialReport(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                reportService.getFinancialReport(period, referenceDate, startDate, endDate)
        );
    }

    @GetMapping("/performance")
    public ResponseEntity<PerformanceReportResponse> getPerformanceReport(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long actorUserId
    ) {
    validatePositiveId(technicianId, "technicianId");
    validatePositiveId(actorUserId, "actorUserId");

    return ResponseEntity.ok(
            reportService.getPerformanceReport(
                    period, referenceDate, startDate, endDate,
                    technicianId, actorUserId
            )
    );
}

    @GetMapping("/comprehensive")
    public ResponseEntity<ComprehensiveReportResponse> getComprehensiveReport(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                reportService.getComprehensiveReport(period, referenceDate, startDate, endDate)
        );
    }

    // =====================================================
    // PDF EXPORT ENDPOINTS
    // =====================================================

    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportReportPdf(
            @RequestParam(defaultValue = "COMPREHENSIVE") ReportType reportType,
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long actorUserId
    ) {
    validatePositiveId(technicianId, "technicianId");
    validatePositiveId(actorUserId, "actorUserId");

    byte[] pdfBytes = reportPdfService.exportPdf(
            reportType, period, referenceDate, startDate,
            endDate, technicianId, actorUserId
    );

    return buildPdfResponse(pdfBytes, reportType, period);
}

    @GetMapping(value = "/operational/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportOperationalPdf(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return exportReportPdf(ReportType.OPERATIONAL, period, referenceDate, startDate, endDate, null, null);
    }

    @GetMapping(value = "/stock/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportStockPdf(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return exportReportPdf(ReportType.STOCK, period, referenceDate, startDate, endDate, null, null);
    }

    @GetMapping(value = "/financial/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportFinancialPdf(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return exportReportPdf(ReportType.FINANCIAL, period, referenceDate, startDate, endDate, null, null);
    }

    @GetMapping(value = "/performance/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPerformancePdf(
            @RequestParam(defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long actorUserId
    ) {
        return exportReportPdf(
                ReportType.PERFORMANCE, period, referenceDate, startDate, endDate, technicianId, actorUserId
        );
    }
    private void validatePositiveId(Long id, String fieldName) {
    if (id != null && id <= 0) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + " must be a positive number"
        );
    }
}
    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdfBytes, ReportType reportType, ReportPeriod period) {
        String filename = String.format(
                "richcomes-%s-%s-report.pdf",
                reportType.name().toLowerCase(),
                period.name().toLowerCase()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}