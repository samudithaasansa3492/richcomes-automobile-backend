package com.richcomes.garage.report.dto;

import java.math.BigDecimal;

public class ChartSeriesDTO {

    private ChartSeriesDTO() {
    }

    public static class TimeSeriesPoint {
        private String label;
        private long jobsCount;
        private long appointmentsCount;
        private BigDecimal revenue;
        private BigDecimal procurementSpend;

        public TimeSeriesPoint() {
        }

        public TimeSeriesPoint(
                String label,
                long jobsCount,
                long appointmentsCount,
                BigDecimal revenue,
                BigDecimal procurementSpend
        ) {
            this.label = label;
            this.jobsCount = jobsCount;
            this.appointmentsCount = appointmentsCount;
            this.revenue = revenue;
            this.procurementSpend = procurementSpend;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public long getJobsCount() {
            return jobsCount;
        }

        public void setJobsCount(long jobsCount) {
            this.jobsCount = jobsCount;
        }

        public long getAppointmentsCount() {
            return appointmentsCount;
        }

        public void setAppointmentsCount(long appointmentsCount) {
            this.appointmentsCount = appointmentsCount;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue(BigDecimal revenue) {
            this.revenue = revenue;
        }

        public BigDecimal getProcurementSpend() {
            return procurementSpend;
        }

        public void setProcurementSpend(BigDecimal procurementSpend) {
            this.procurementSpend = procurementSpend;
        }
    }

    public static class ChartSegment {
        private String label;
        private long count;
        private BigDecimal value;
        private BigDecimal percentage;

        public ChartSegment() {
        }

        public ChartSegment(
                String label,
                long count,
                BigDecimal value,
                BigDecimal percentage
        ) {
            this.label = label;
            this.count = count;
            this.value = value;
            this.percentage = percentage;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public BigDecimal getPercentage() {
            return percentage;
        }

        public void setPercentage(BigDecimal percentage) {
            this.percentage = percentage;
        }
    }
}