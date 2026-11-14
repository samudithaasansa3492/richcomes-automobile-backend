package com.richcomes.garage.report.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.richcomes.garage.report.dto.ChartSeriesDTO;
import com.richcomes.garage.report.dto.ComprehensiveReportResponse;
import com.richcomes.garage.report.dto.FinancialReportResponse;
import com.richcomes.garage.report.dto.OperationalReportResponse;
import com.richcomes.garage.report.dto.PerformanceReportResponse;
import com.richcomes.garage.report.dto.ReportMetadata;
import com.richcomes.garage.report.dto.StockReportResponse;
import com.richcomes.garage.report.enums.ReportPeriod;
import com.richcomes.garage.report.enums.ReportType;
import com.richcomes.garage.report.service.ReportPdfService;
import com.richcomes.garage.report.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportPdfServiceImpl implements ReportPdfService {

    private static final Color BRAND_DARK = new Color(20, 40, 75);
    private static final Color BRAND_ACCENT = new Color(37, 99, 235);
    private static final Color SOFT_BG = new Color(241, 245, 249);
    private static final Color BORDER_GRAY = new Color(203, 213, 225);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color BAR_TRACK = new Color(226, 232, 240);

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(219, 234, 254));
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_DARK);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
    private static final Font BODY_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, BRAND_DARK);
    private static final Font KPI_LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_MUTED);
    private static final Font KPI_VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_DARK);
    private static final Font NOTE_FONT = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, TEXT_MUTED);

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportService reportService;

    public ReportPdfServiceImpl(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public byte[] exportPdf(
            ReportType reportType,
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate,
            Long technicianId,
            Long actorUserId
    ) {
        ReportType effectiveType = reportType != null ? reportType : ReportType.COMPREHENSIVE;
        ReportPeriod effectivePeriod = period != null ? period : ReportPeriod.MONTHLY;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 32, 32, 36, 42);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterPageEvent());
            document.open();

            switch (effectiveType) {
                case OPERATIONAL -> {
                    OperationalReportResponse data =
                            reportService.getOperationalReport(effectivePeriod, referenceDate, startDate, endDate);
                    addHeaderBanner(document, "OPERATIONAL ANALYTICS REPORT", data.getMetadata());
                    writeOperationalSection(document, data);
                }
                case STOCK -> {
                    StockReportResponse data =
                            reportService.getStockReport(effectivePeriod, referenceDate, startDate, endDate);
                    addHeaderBanner(document, "INVENTORY & STOCK ANALYTICS REPORT", data.getMetadata());
                    writeStockSection(document, data);
                }
                case FINANCIAL -> {
                    FinancialReportResponse data =
                            reportService.getFinancialReport(effectivePeriod, referenceDate, startDate, endDate);
                    addHeaderBanner(document, "FINANCIAL ANALYTICS REPORT", data.getMetadata());
                    writeFinancialSection(document, data);
                }
                case PERFORMANCE -> {
                    PerformanceReportResponse data = reportService.getPerformanceReport(
                            effectivePeriod, referenceDate, startDate, endDate, technicianId, actorUserId
                    );
                    addHeaderBanner(document, "USER & TECHNICIAN PERFORMANCE REPORT", data.getMetadata());
                    writePerformanceSection(document, data);
                }
                case COMPREHENSIVE -> {
                    ComprehensiveReportResponse data =
                            reportService.getComprehensiveReport(effectivePeriod, referenceDate, startDate, endDate);
                    addHeaderBanner(document, "EXECUTIVE & COMPREHENSIVE GARAGE REPORT", data.getMetadata());
                    if (data.getFinancial() != null) {
                        writeFinancialSection(document, data.getFinancial());
                    }
                    if (data.getOperational() != null) {
                        document.newPage();
                        writeOperationalSection(document, data.getOperational());
                    }
                    if (data.getStock() != null) {
                        document.newPage();
                        writeStockSection(document, data.getStock());
                    }
                    if (data.getPerformance() != null) {
                        document.newPage();
                        writePerformanceSection(document, data.getPerformance());
                    }
                }
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate PDF report",
                    ex
            );
        } catch (Exception ex) {
            if (ex instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error while exporting PDF report",
                    ex
            );
        }
    }

    private void addHeaderBanner(Document document, String title, ReportMetadata metadata) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(12f);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_DARK);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(14f);

        Paragraph company = new Paragraph("RICHCOMES AUTOMOBILE — VEHICLE SERVICE & GARAGE MANAGEMENT", SUBTITLE_FONT);
        company.setSpacingAfter(4f);
        cell.addElement(company);

        Paragraph mainTitle = new Paragraph(title, TITLE_FONT);
        mainTitle.setSpacingAfter(6f);
        cell.addElement(mainTitle);

        String periodText = metadata != null && metadata.getPeriod() != null ? metadata.getPeriod().name() : "-";
        String startText = metadata != null && metadata.getStartDate() != null ? metadata.getStartDate().toString() : "-";
        String endText = metadata != null && metadata.getEndDate() != null ? metadata.getEndDate().toString() : "-";
        String genText = metadata != null && metadata.getGeneratedAt() != null ? metadata.getGeneratedAt().format(TS_FORMAT) : "-";
        String tzText = metadata != null && metadata.getTimeZone() != null ? metadata.getTimeZone() : "Asia/Colombo";

        String metaLine = String.format(
                "Period: %s   |   Date Range: %s to %s   |   Generated: %s (%s)",
                periodText, startText, endText, genText, tzText
        );
        cell.addElement(new Paragraph(metaLine, SUBTITLE_FONT));

        banner.addCell(cell);
        document.add(banner);
    }

    private void writeOperationalSection(Document document, OperationalReportResponse data) throws DocumentException {
        addSectionTitle(document, "1. Operational KPI Summary");

        PdfPTable kpis = createKpiGrid(new String[][]{
                {"Total Appointments", String.valueOf(data.getTotalAppointments())},
                {"Appointment Done Rate", formatPercent(data.getAppointmentCompletionRate())},
                {"Total Service Jobs", String.valueOf(data.getTotalJobs())},
                {"Job Completion Rate", formatPercent(data.getJobCompletionRate())},
                {"Avg Completion Time", formatDecimal(data.getAverageJobCompletionHours()) + " hrs"},
                {"Active Customers / Vehicles", data.getActiveCustomers() + " / " + data.getActiveVehicles()},
                {"Completed Histories", String.valueOf(data.getCompletedServiceHistoriesInPeriod())},
                {"Due Reminders / Failed Notifications",
                    data.getDueRemindersCount() + " / "
                    + data.getFailedNotificationsInPeriod()}
        });
        document.add(kpis);

        addSectionTitle(document, "Job Status Distribution");
        document.add(createVisualBreakdownTable(data.getJobStatusDistribution(), "Status"));

        addSectionTitle(document, "Appointment Status Distribution");
        document.add(createVisualBreakdownTable(data.getAppointmentStatusDistribution(), "Appointment Status"));

        addSectionTitle(document, "Top Services Performed");
        PdfPTable topTable = createStyledTable(new float[]{3.2f, 1.3f, 1.5f, 2f});
        addTableHeader(topTable, "Service Name", "Count", "Labour Hours", "Labour Amount (LKR)");
        List<OperationalReportResponse.TopServiceSummary> topServices = safeList(data.getTopServicesPerformed());
        if (topServices.isEmpty()) {
            addEmptyRow(topTable, 4, "No service job items recorded in this period.");
        } else {
            boolean alt = false;
            for (OperationalReportResponse.TopServiceSummary svc : topServices) {
                addRowCell(topTable, svc.getServiceName(), Element.ALIGN_LEFT, alt);
                addRowCell(topTable, String.valueOf(svc.getTimesPerformed()), Element.ALIGN_RIGHT, alt);
                addRowCell(topTable, formatDecimal(svc.getTotalLabourHours()), Element.ALIGN_RIGHT, alt);
                addRowCell(topTable, formatCurrency(svc.getTotalLabourAmount()), Element.ALIGN_RIGHT, alt);
                alt = !alt;
            }
        }
        document.add(topTable);
    }

    private void writeStockSection(Document document, StockReportResponse data) throws DocumentException {
        addSectionTitle(document, "2. Inventory & Stock Valuation");

        PdfPTable kpis = createKpiGrid(new String[][]{
                {"Active / Total Items", data.getActiveStockItems() + " / " + data.getTotalStockItems()},
                {"Total Units in Stock", String.valueOf(data.getTotalUnitsInStock())},
                {"Cost Valuation", formatCurrency(data.getTotalInventoryCostValue())},
                {"Selling Valuation", formatCurrency(data.getTotalInventorySellingValue())},
                {"Low Stock Alerts", String.valueOf(data.getLowStockCount())},
                {"Confirmed GRNs", String.valueOf(data.getConfirmedGrnsInPeriod())},
                {"GRN Procurement Spend", formatCurrency(data.getConfirmedGrnTotalSpend())},
                {"Stock IN / OUT Qty", data.getStockInQuantity() + " / " + data.getStockOutQuantity()}
        });
        document.add(kpis);

        addSectionTitle(document, "Stock Movements in Period");
        document.add(createVisualBreakdownTable(data.getMovementTypeDistribution(), "Movement Type"));

        addSectionTitle(document, "Category Valuation Breakdown");
        PdfPTable catTable = createStyledTable(new float[]{2.5f, 1f, 1.2f, 1.8f, 1.8f});
        addTableHeader(catTable, "Category", "Items", "Total Qty", "Cost Value (LKR)", "Selling Value (LKR)");
        List<StockReportResponse.CategoryStockSummary> categories = safeList(data.getCategoryBreakdown());
        if (categories.isEmpty()) {
            addEmptyRow(catTable, 5, "No stock categories found.");
        } else {
            boolean alt = false;
            for (StockReportResponse.CategoryStockSummary cat : categories) {
                addRowCell(catTable, cat.getCategoryName(), Element.ALIGN_LEFT, alt);
                addRowCell(catTable, String.valueOf(cat.getItemCount()), Element.ALIGN_RIGHT, alt);
                addRowCell(catTable, String.valueOf(cat.getTotalQuantity()), Element.ALIGN_RIGHT, alt);
                addRowCell(catTable, formatCurrency(cat.getCostValuation()), Element.ALIGN_RIGHT, alt);
                addRowCell(catTable, formatCurrency(cat.getSellingValuation()), Element.ALIGN_RIGHT, alt);
                alt = !alt;
            }
        }
        document.add(catTable);

        addSectionTitle(document, "Supplier Procurement Spend Breakdown");
        PdfPTable supTable = createStyledTable(new float[]{3.2f, 1.4f, 2f});
        addTableHeader(supTable, "Supplier Name", "Confirmed GRNs", "Total Spend (LKR)");
        List<StockReportResponse.SupplierSpendSummary> suppliers = safeList(data.getSupplierSpendBreakdown());
        if (suppliers.isEmpty()) {
            addEmptyRow(supTable, 3, "No confirmed supplier GRNs recorded in this period.");
        } else {
            boolean alt = false;
            for (StockReportResponse.SupplierSpendSummary sup : suppliers) {
                addRowCell(supTable, sup.getSupplierName(), Element.ALIGN_LEFT, alt);
                addRowCell(supTable, String.valueOf(sup.getConfirmedGrnCount()), Element.ALIGN_RIGHT, alt);
                addRowCell(supTable, formatCurrency(sup.getTotalSpend()), Element.ALIGN_RIGHT, alt);
                alt = !alt;
            }
        }
        document.add(supTable);

        addSectionTitle(document, "Low Stock Alert Items");
        PdfPTable lowTable = createStyledTable(new float[]{1.4f, 2.5f, 1.8f, 1f, 1f, 1f});
        addTableHeader(lowTable, "Code", "Item Name", "Category", "Current", "Reorder", "Deficit");
        List<StockReportResponse.LowStockItemAlert> lowItems = safeList(data.getLowStockItems());
        if (lowItems.isEmpty()) {
            addEmptyRow(lowTable, 6, "All active stock items are above reorder level.");
        } else {
            boolean alt = false;
            for (StockReportResponse.LowStockItemAlert item : lowItems) {
                addRowCell(lowTable, item.getItemCode(), Element.ALIGN_LEFT, alt);
                addRowCell(lowTable, item.getItemName(), Element.ALIGN_LEFT, alt);
                addRowCell(lowTable, item.getCategoryName(), Element.ALIGN_LEFT, alt);
                addRowCell(lowTable, String.valueOf(item.getCurrentQuantity()), Element.ALIGN_RIGHT, alt);
                addRowCell(lowTable, String.valueOf(item.getReorderLevel()), Element.ALIGN_RIGHT, alt);
                addRowCell(lowTable, String.valueOf(item.getDeficit()), Element.ALIGN_RIGHT, alt);
                alt = !alt;
            }
        }
        document.add(lowTable);
    }

    private void writeFinancialSection(Document document, FinancialReportResponse data) throws DocumentException {
        addSectionTitle(document, "3. Financial Performance & Trend");

        PdfPTable kpis = createKpiGrid(new String[][]{
                {"Service History Revenue", formatCurrency(data.getRecordedServiceRevenue())},
                {"Avg Service Ticket", formatCurrency(data.getAverageServiceTicketValue())},
                {"Job Billable Total", formatCurrency(data.getJobBillableTotal())},
                {"GRN Procurement Spend", formatCurrency(data.getConfirmedGrnProcurementSpend())},
                {"Revenue Less Procurement (Estimate)",
                formatCurrency(data.getNetOperatingCashEstimate())},
                {"Draft GRN Pending", formatCurrency(data.getDraftGrnPendingValue())},
                {"Active Staff Daily Rate", formatCurrency(data.getActiveStaffDailyRateTotal())},
                {"Customer Credit Pool", formatCurrency(data.getTotalCustomerCreditLimitPool())}
        });
        document.add(kpis);

        if (data.getBillingIntegrationNote() != null && !data.getBillingIntegrationNote().isBlank()) {
            Paragraph note = new Paragraph("Note: " + data.getBillingIntegrationNote(), NOTE_FONT);
            note.setSpacingAfter(8f);
            document.add(note);
        }

        addSectionTitle(document, "Billable Revenue Composition");
        document.add(createVisualBreakdownTable(data.getRevenueComposition(), "Revenue Stream"));

        addSectionTitle(document, "Period Revenue vs Procurement Trend");
        PdfPTable trendTable = createStyledTable(new float[]{1.8f, 1.1f, 1.3f, 2f, 2f});
        addTableHeader(trendTable, "Bucket", "Jobs", "Appointments", "Revenue (LKR)", "GRN Spend (LKR)");
        List<ChartSeriesDTO.TimeSeriesPoint> trendPoints = safeList(data.getFinancialTrend());
        if (trendPoints.isEmpty()) {
            addEmptyRow(trendTable, 5, "No trend records available for the selected range.");
        } else {
            boolean alt = false;
            for (ChartSeriesDTO.TimeSeriesPoint point : trendPoints) {
                addRowCell(trendTable, point.getLabel(), Element.ALIGN_LEFT, alt);
                addRowCell(trendTable, String.valueOf(point.getJobsCount()), Element.ALIGN_RIGHT, alt);
                addRowCell(trendTable, String.valueOf(point.getAppointmentsCount()), Element.ALIGN_RIGHT, alt);
                addRowCell(trendTable, formatCurrency(point.getRevenue()), Element.ALIGN_RIGHT, alt);
                addRowCell(trendTable, formatCurrency(point.getProcurementSpend()), Element.ALIGN_RIGHT, alt);
                alt = !alt;
            }
        }
        document.add(trendTable);
    }

    private void writePerformanceSection(Document document, PerformanceReportResponse data) throws DocumentException {
        addSectionTitle(document, "4. User & Technician Performance Analytics");

        PdfPTable kpis = createKpiGrid(new String[][]{
                {"Active / Total Staff", data.getActiveEmployees() + " / " + data.getTotalEmployees()},
                {"Technicians Tracked", String.valueOf(data.getActiveTechniciansTracked())},
                {"System Users Tracked", String.valueOf(data.getSystemActorsTracked())},
                {"Audit Events in Period", String.valueOf(data.getTotalAuditEventsInPeriod())}
        });
        document.add(kpis);

        addSectionTitle(document, "Employee Position Distribution");
        document.add(createVisualBreakdownTable(data.getEmployeePositionDistribution(), "Position"));

        addSectionTitle(document, "Technician Job & Revenue Performance");
        PdfPTable techTable = createStyledTable(new float[]{1.2f, 1f, 1f, 1.2f, 1.2f, 1.8f, 1.8f});
        addTableHeader(techTable, "Tech ID", "Assigned", "Done", "Rate %", "Hours", "Billable (LKR)", "History Rev (LKR)");
        List<PerformanceReportResponse.TechnicianPerformanceItem> techItems = safeList(data.getTechnicianPerformances());
        if (techItems.isEmpty()) {
            addEmptyRow(techTable, 7, "No technician assignments recorded in this period.");
        } else {
            boolean alt = false;
            for (PerformanceReportResponse.TechnicianPerformanceItem t : techItems) {
                String techLabel = t.getTechnicianId() != null ? "#" + t.getTechnicianId() : "-";
                addRowCell(techTable, techLabel, Element.ALIGN_LEFT, alt, BODY_BOLD_FONT);
                addRowCell(techTable, String.valueOf(t.getTotalJobsAssigned()), Element.ALIGN_RIGHT, alt);
                addRowCell(techTable, String.valueOf(t.getCompletedJobs()), Element.ALIGN_RIGHT, alt);
                addRowCell(techTable, formatPercent(t.getCompletionRate()), Element.ALIGN_RIGHT, alt);
                addRowCell(techTable, formatDecimal(t.getTotalLabourHours()), Element.ALIGN_RIGHT, alt);
                addRowCell(techTable, formatCurrency(t.getTotalBillableValue()), Element.ALIGN_RIGHT, alt);
                addRowCell(techTable, formatCurrency(t.getServiceHistoryRevenue()), Element.ALIGN_RIGHT, alt);
                alt = !alt;
            }
        }
        document.add(techTable);

        addSectionTitle(document, "System User Activity (Audit Trail)");
        PdfPTable userTable = createStyledTable(new float[]{1.3f, 1.2f, 2.5f, 2.5f, 2f});
        addTableHeader(userTable, "User ID", "Actions", "By Action Type", "By Entity", "Last Activity");
        List<PerformanceReportResponse.UserAuditActivityItem> userItems = safeList(data.getUserAuditActivities());
        if (userItems.isEmpty()) {
            addEmptyRow(userTable, 5, "No user audit activity recorded in this period.");
        } else {
            boolean alt = false;
            for (PerformanceReportResponse.UserAuditActivityItem u : userItems) {
                String actorLabel = u.getActorUserId() != null ? "User #" + u.getActorUserId() : "-";
                addRowCell(userTable, actorLabel, Element.ALIGN_LEFT, alt, BODY_BOLD_FONT);
                addRowCell(userTable, String.valueOf(u.getTotalActions()), Element.ALIGN_RIGHT, alt);
                addRowCell(userTable, formatMap(u.getActionsByType()), Element.ALIGN_LEFT, alt);
                addRowCell(userTable, formatMap(u.getActionsByEntity()), Element.ALIGN_LEFT, alt);
                addRowCell(
                        userTable,
                        u.getLastActivityAt() != null ? u.getLastActivityAt().format(TS_FORMAT) : "-",
                        Element.ALIGN_LEFT,
                        alt
                );
                alt = !alt;
            }
        }
        document.add(userTable);
    }

    // =====================================================
    // PDF LAYOUT & VISUALIZATION HELPERS
    // =====================================================

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph paragraph = new Paragraph(title, SECTION_FONT);
        paragraph.setSpacingBefore(10f);
        paragraph.setSpacingAfter(5f);
        document.add(paragraph);
    }

    private PdfPTable createKpiGrid(String[][] items) throws DocumentException {
        PdfPTable grid = new PdfPTable(4);
        grid.setWidthPercentage(100);
        grid.setSpacingAfter(8f);

        for (String[] pair : items) {
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(SOFT_BG);
            cell.setBorderColor(BORDER_GRAY);
            cell.setPadding(7f);

            Paragraph label = new Paragraph(pair[0], KPI_LABEL_FONT);
            label.setSpacingAfter(2f);
            cell.addElement(label);
            cell.addElement(new Paragraph(pair[1], KPI_VALUE_FONT));
            grid.addCell(cell);
        }

        int remainder = items.length % 4;
        if (remainder != 0) {
            for (int i = 0; i < 4 - remainder; i++) {
                PdfPCell empty = new PdfPCell(new Phrase(""));
                empty.setBorder(Rectangle.NO_BORDER);
                grid.addCell(empty);
            }
        }
        return grid;
    }

    private PdfPTable createVisualBreakdownTable(
            List<ChartSeriesDTO.ChartSegment> segments,
            String labelHeader
    ) throws DocumentException {
        PdfPTable table = createStyledTable(new float[]{2.4f, 1.1f, 1.5f, 1.1f, 2.8f});
        addTableHeader(table, labelHeader, "Count", "Value", "%", "Visual Distribution");

        List<ChartSeriesDTO.ChartSegment> safeSegments = safeList(segments);
        if (safeSegments.isEmpty()) {
            addEmptyRow(table, 5, "No breakdown data available.");
            return table;
        }

        boolean alt = false;
        for (ChartSeriesDTO.ChartSegment seg : safeSegments) {
            addRowCell(table, seg.getLabel(), Element.ALIGN_LEFT, alt);
            addRowCell(table, String.valueOf(seg.getCount()), Element.ALIGN_RIGHT, alt);
            addRowCell(table, formatDecimal(seg.getValue()), Element.ALIGN_RIGHT, alt);
            addRowCell(table, formatPercent(seg.getPercentage()), Element.ALIGN_RIGHT, alt);

            PdfPCell barContainer = new PdfPCell();
            barContainer.setBorderColor(BORDER_GRAY);
            barContainer.setBackgroundColor(alt ? SOFT_BG : Color.WHITE);
            barContainer.setPadding(5f);
            barContainer.setVerticalAlignment(Element.ALIGN_MIDDLE);
            barContainer.addElement(buildHorizontalBar(seg.getPercentage()));
            table.addCell(barContainer);
            alt = !alt;
        }
        return table;
    }

    private PdfPTable buildHorizontalBar(BigDecimal percentage) throws DocumentException {
        float pct = percentage != null ? percentage.floatValue() : 0f;
        pct = Math.max(0f, Math.min(100f, pct));

        if (pct <= 0f) {
            PdfPTable bar = new PdfPTable(1);
            bar.setWidthPercentage(100);
            bar.addCell(createBarCell(BAR_TRACK));
            return bar;
        }

        if (pct >= 100f) {
            PdfPTable bar = new PdfPTable(1);
            bar.setWidthPercentage(100);
            bar.addCell(createBarCell(BRAND_ACCENT));
            return bar;
        }

        PdfPTable bar = new PdfPTable(2);
        bar.setWidthPercentage(100);
        bar.setWidths(new float[]{pct, 100f - pct});
        bar.addCell(createBarCell(BRAND_ACCENT));
        bar.addCell(createBarCell(BAR_TRACK));
        return bar;
    }

    private PdfPCell createBarCell(Color backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBackgroundColor(backgroundColor);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.setFixedHeight(8f);
        return cell;
    }

    private PdfPTable createStyledTable(float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(widths.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingAfter(8f);
        table.setHeaderRows(1);
        return table;
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            cell.setBackgroundColor(BRAND_DARK);
            cell.setBorderColor(BRAND_DARK);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private void addRowCell(PdfPTable table, String text, int align, boolean alt) {
        addRowCell(table, text, align, alt, BODY_FONT);
    }

    private void addRowCell(PdfPTable table, String text, int align, boolean alt, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(alt ? SOFT_BG : Color.WHITE);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addEmptyRow(PdfPTable table, int colSpan, String message) {
        PdfPCell cell = new PdfPCell(new Phrase(message, NOTE_FONT));
        cell.setColspan(colSpan);
        cell.setPadding(8f);
        cell.setBorderColor(BORDER_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private String formatMap(Map<String, Long> map) {
        return map != null && !map.isEmpty() ? map.toString() : "-";
    }

    private String formatPercent(BigDecimal value) {
        BigDecimal amount = (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return amount.toPlainString() + "%";
    }

    private String formatCurrency(BigDecimal value) {
        BigDecimal amount = (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "LKR " + nf.format(amount);
    }

    private String formatDecimal(BigDecimal value) {
        BigDecimal amount = (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    private static class FooterPageEvent extends PdfPageEventHelper {
        private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Phrase footer = new Phrase(
                    "Richcomes Automobile Garage Management System  |  Page " + writer.getPageNumber(),
                    FOOTER_FONT
            );
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    footer,
                    (document.left() + document.right()) / 2f,
                    document.bottom() - 18f,
                    0
            );
        }
    }
}