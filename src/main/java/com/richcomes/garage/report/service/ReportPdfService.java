package com.richcomes.garage.report.service;

import com.richcomes.garage.report.enums.ReportPeriod;
import com.richcomes.garage.report.enums.ReportType;

import java.time.LocalDate;

public interface ReportPdfService {

    byte[] exportPdf(
            ReportType reportType,
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate,
            Long technicianId,
            Long actorUserId
    );
}