package com.richcomes.garage.report.dto;

import com.richcomes.garage.report.enums.ReportPeriod;
import com.richcomes.garage.report.enums.ReportType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReportMetadata {

    private ReportType reportType;
    private ReportPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;
    private String timeZone;
    private String currency;

    public ReportMetadata() {
    }

    public ReportMetadata(
            ReportType reportType,
            ReportPeriod period,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime generatedAt,
            String timeZone,
            String currency
    ) {
        this.reportType = reportType;
        this.period = period;
        this.startDate = startDate;
        this.endDate = endDate;
        this.generatedAt = generatedAt;
        this.timeZone = timeZone;
        this.currency = currency;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public ReportPeriod getPeriod() {
        return period;
    }

    public void setPeriod(ReportPeriod period) {
        this.period = period;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}