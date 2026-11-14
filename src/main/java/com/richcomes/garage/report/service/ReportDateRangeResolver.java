package com.richcomes.garage.report.service;

import com.richcomes.garage.report.enums.ReportPeriod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

@Component
public class ReportDateRangeResolver {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    public record DateWindow(LocalDate startDate, LocalDate endDate) {
    }

    public DateWindow resolve(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        ReportPeriod effectivePeriod =
        period != null ? period : ReportPeriod.MONTHLY;

LocalDate anchor =
        referenceDate != null
                ? referenceDate
                : LocalDate.now(BUSINESS_ZONE);

// Validate date parameters
if (effectivePeriod != ReportPeriod.CUSTOM
        && (startDate != null || endDate != null)) {

    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "startDate and endDate can only be used when period is CUSTOM"
    );
}

if (effectivePeriod == ReportPeriod.CUSTOM
        && referenceDate != null) {

    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "referenceDate cannot be used when period is CUSTOM"
    );
}

return switch (effectivePeriod) {
            case WEEKLY -> new DateWindow(
                    anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case MONTHLY -> new DateWindow(
                    anchor.withDayOfMonth(1),
                    anchor.withDayOfMonth(anchor.lengthOfMonth())
            );
            case YEARLY -> new DateWindow(
                    anchor.withDayOfYear(1),
                    anchor.withDayOfYear(anchor.lengthOfYear())
            );
            case CUSTOM -> {
                if (startDate == null || endDate == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "startDate and endDate are required when period is CUSTOM"
                    );
                }
                if (startDate.isAfter(endDate)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "startDate cannot be after endDate"
                    );
                }
                yield new DateWindow(startDate, endDate);
            }
        };
    }
}