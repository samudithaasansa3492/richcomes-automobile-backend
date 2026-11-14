package com.richcomes.garage.report.dto;

import java.math.BigDecimal;
import java.util.List;

public class OperationalReportResponse {

    private ReportMetadata metadata;

    // Appointments
    private long totalAppointments;
    private long pendingAppointments;
    private long approvedAppointments;
    private long rejectedAppointments;
    private long doneAppointments;
    private long notCompletedAppointments;
    private BigDecimal appointmentCompletionRate;

    // Jobs
    private long totalJobs;
    private long pendingJobs;
    private long inProgressJobs;
    private long completedJobs;
    private long closedJobs;
    private long cancelledJobs;
    private BigDecimal jobCompletionRate;
    private BigDecimal averageJobCompletionHours;

    // Customer & Vehicle Growth
    private long totalCustomers;
    private long activeCustomers;
    private long newCustomersInPeriod;
    private long totalVehicles;
    private long activeVehicles;
    private long newVehiclesInPeriod;

    // Service History, Reminders & Notifications
    private long completedServiceHistoriesInPeriod;
    private long totalReminders;
    private long pendingReminders;
    private long sentReminders;
    private long cancelledReminders;
    private long dueRemindersCount;
    private long notificationsInPeriod;
    private long sentNotificationsInPeriod;
    private long failedNotificationsInPeriod;
    private long pendingNotificationsInPeriod;

    // Visualizations
    private List<TopServiceSummary> topServicesPerformed;
    private List<ChartSeriesDTO.ChartSegment> jobStatusDistribution;
    private List<ChartSeriesDTO.ChartSegment> appointmentStatusDistribution;

    public OperationalReportResponse() {
    }

    public static class TopServiceSummary {
        private Long serviceItemId;
        private String serviceName;
        private long timesPerformed;
        private BigDecimal totalLabourHours;
        private BigDecimal totalLabourAmount;

        public TopServiceSummary() {
        }

        public TopServiceSummary(
                Long serviceItemId,
                String serviceName,
                long timesPerformed,
                BigDecimal totalLabourHours,
                BigDecimal totalLabourAmount
        ) {
            this.serviceItemId = serviceItemId;
            this.serviceName = serviceName;
            this.timesPerformed = timesPerformed;
            this.totalLabourHours = totalLabourHours;
            this.totalLabourAmount = totalLabourAmount;
        }

        public Long getServiceItemId() {
            return serviceItemId;
        }

        public void setServiceItemId(Long serviceItemId) {
            this.serviceItemId = serviceItemId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public long getTimesPerformed() {
            return timesPerformed;
        }

        public void setTimesPerformed(long timesPerformed) {
            this.timesPerformed = timesPerformed;
        }

        public BigDecimal getTotalLabourHours() {
            return totalLabourHours;
        }

        public void setTotalLabourHours(BigDecimal totalLabourHours) {
            this.totalLabourHours = totalLabourHours;
        }

        public BigDecimal getTotalLabourAmount() {
            return totalLabourAmount;
        }

        public void setTotalLabourAmount(BigDecimal totalLabourAmount) {
            this.totalLabourAmount = totalLabourAmount;
        }
    }

    public ReportMetadata getMetadata() { return metadata; }
    public void setMetadata(ReportMetadata metadata) { this.metadata = metadata; }
    public long getTotalAppointments() { return totalAppointments; }
    public void setTotalAppointments(long totalAppointments) { this.totalAppointments = totalAppointments; }
    public long getPendingAppointments() { return pendingAppointments; }
    public void setPendingAppointments(long pendingAppointments) { this.pendingAppointments = pendingAppointments; }
    public long getApprovedAppointments() { return approvedAppointments; }
    public void setApprovedAppointments(long approvedAppointments) { this.approvedAppointments = approvedAppointments; }
    public long getRejectedAppointments() { return rejectedAppointments; }
    public void setRejectedAppointments(long rejectedAppointments) { this.rejectedAppointments = rejectedAppointments; }
    public long getDoneAppointments() { return doneAppointments; }
    public void setDoneAppointments(long doneAppointments) { this.doneAppointments = doneAppointments; }
    public long getNotCompletedAppointments() { return notCompletedAppointments; }
    public void setNotCompletedAppointments(long notCompletedAppointments) { this.notCompletedAppointments = notCompletedAppointments; }
    public BigDecimal getAppointmentCompletionRate() { return appointmentCompletionRate; }
    public void setAppointmentCompletionRate(BigDecimal appointmentCompletionRate) { this.appointmentCompletionRate = appointmentCompletionRate; }
    public long getTotalJobs() { return totalJobs; }
    public void setTotalJobs(long totalJobs) { this.totalJobs = totalJobs; }
    public long getPendingJobs() { return pendingJobs; }
    public void setPendingJobs(long pendingJobs) { this.pendingJobs = pendingJobs; }
    public long getInProgressJobs() { return inProgressJobs; }
    public void setInProgressJobs(long inProgressJobs) { this.inProgressJobs = inProgressJobs; }
    public long getCompletedJobs() { return completedJobs; }
    public void setCompletedJobs(long completedJobs) { this.completedJobs = completedJobs; }
    public long getClosedJobs() { return closedJobs; }
    public void setClosedJobs(long closedJobs) { this.closedJobs = closedJobs; }
    public long getCancelledJobs() { return cancelledJobs; }
    public void setCancelledJobs(long cancelledJobs) { this.cancelledJobs = cancelledJobs; }
    public BigDecimal getJobCompletionRate() { return jobCompletionRate; }
    public void setJobCompletionRate(BigDecimal jobCompletionRate) { this.jobCompletionRate = jobCompletionRate; }
    public BigDecimal getAverageJobCompletionHours() { return averageJobCompletionHours; }
    public void setAverageJobCompletionHours(BigDecimal averageJobCompletionHours) { this.averageJobCompletionHours = averageJobCompletionHours; }
    public long getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(long totalCustomers) { this.totalCustomers = totalCustomers; }
    public long getActiveCustomers() { return activeCustomers; }
    public void setActiveCustomers(long activeCustomers) { this.activeCustomers = activeCustomers; }
    public long getNewCustomersInPeriod() { return newCustomersInPeriod; }
    public void setNewCustomersInPeriod(long newCustomersInPeriod) { this.newCustomersInPeriod = newCustomersInPeriod; }
    public long getTotalVehicles() { return totalVehicles; }
    public void setTotalVehicles(long totalVehicles) { this.totalVehicles = totalVehicles; }
    public long getActiveVehicles() { return activeVehicles; }
    public void setActiveVehicles(long activeVehicles) { this.activeVehicles = activeVehicles; }
    public long getNewVehiclesInPeriod() { return newVehiclesInPeriod; }
    public void setNewVehiclesInPeriod(long newVehiclesInPeriod) { this.newVehiclesInPeriod = newVehiclesInPeriod; }
    public long getCompletedServiceHistoriesInPeriod() { return completedServiceHistoriesInPeriod; }
    public void setCompletedServiceHistoriesInPeriod(long completedServiceHistoriesInPeriod) { this.completedServiceHistoriesInPeriod = completedServiceHistoriesInPeriod; }
    public long getTotalReminders() { return totalReminders; }
    public void setTotalReminders(long totalReminders) { this.totalReminders = totalReminders; }
    public long getPendingReminders() { return pendingReminders; }
    public void setPendingReminders(long pendingReminders) { this.pendingReminders = pendingReminders; }
    public long getSentReminders() { return sentReminders; }
    public void setSentReminders(long sentReminders) { this.sentReminders = sentReminders; }
    public long getCancelledReminders() { return cancelledReminders; }
    public void setCancelledReminders(long cancelledReminders) { this.cancelledReminders = cancelledReminders; }
    public long getDueRemindersCount() { return dueRemindersCount; }
    public void setDueRemindersCount(long dueRemindersCount) { this.dueRemindersCount = dueRemindersCount; }
    public long getNotificationsInPeriod() { return notificationsInPeriod; }
    public void setNotificationsInPeriod(long notificationsInPeriod) { this.notificationsInPeriod = notificationsInPeriod; }
    public long getSentNotificationsInPeriod() { return sentNotificationsInPeriod; }
    public void setSentNotificationsInPeriod(long sentNotificationsInPeriod) { this.sentNotificationsInPeriod = sentNotificationsInPeriod; }
    public long getFailedNotificationsInPeriod() { return failedNotificationsInPeriod; }
    public void setFailedNotificationsInPeriod(long failedNotificationsInPeriod) { this.failedNotificationsInPeriod = failedNotificationsInPeriod; }
    public long getPendingNotificationsInPeriod() { return pendingNotificationsInPeriod; }
    public void setPendingNotificationsInPeriod(long pendingNotificationsInPeriod) { this.pendingNotificationsInPeriod = pendingNotificationsInPeriod; }
    public List<TopServiceSummary> getTopServicesPerformed() { return topServicesPerformed; }
    public void setTopServicesPerformed(List<TopServiceSummary> topServicesPerformed) { this.topServicesPerformed = topServicesPerformed; }
    public List<ChartSeriesDTO.ChartSegment> getJobStatusDistribution() { return jobStatusDistribution; }
    public void setJobStatusDistribution(List<ChartSeriesDTO.ChartSegment> jobStatusDistribution) { this.jobStatusDistribution = jobStatusDistribution; }
    public List<ChartSeriesDTO.ChartSegment> getAppointmentStatusDistribution() { return appointmentStatusDistribution; }
    public void setAppointmentStatusDistribution(List<ChartSeriesDTO.ChartSegment> appointmentStatusDistribution) { this.appointmentStatusDistribution = appointmentStatusDistribution; }
}