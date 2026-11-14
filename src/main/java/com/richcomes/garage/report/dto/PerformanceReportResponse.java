package com.richcomes.garage.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PerformanceReportResponse {

    private ReportMetadata metadata;

    // Summary
    private long activeEmployees;
    private long totalEmployees;
    private long activeTechniciansTracked;
    private long systemActorsTracked;
    private long totalAuditEventsInPeriod;

    private List<TechnicianPerformanceItem> technicianPerformances;
    private List<UserAuditActivityItem> userAuditActivities;
    private List<EmployeeRosterSummary> employeeRoster;
    private List<ChartSeriesDTO.ChartSegment> employeePositionDistribution;

    public PerformanceReportResponse() {
    }

    public static class TechnicianPerformanceItem {
        private Long technicianId;
        private long totalJobsAssigned;
        private long completedJobs;
        private long inProgressJobs;
        private long cancelledJobs;
        private BigDecimal completionRate;
        private BigDecimal totalLabourHours;
        private BigDecimal totalBillableValue;
        private BigDecimal serviceHistoryRevenue;
        private long completedHistoryCount;
        private BigDecimal averageCompletionHours;

        public TechnicianPerformanceItem() {
        }

        public Long getTechnicianId() { return technicianId; }
        public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
        public long getTotalJobsAssigned() { return totalJobsAssigned; }
        public void setTotalJobsAssigned(long totalJobsAssigned) { this.totalJobsAssigned = totalJobsAssigned; }
        public long getCompletedJobs() { return completedJobs; }
        public void setCompletedJobs(long completedJobs) { this.completedJobs = completedJobs; }
        public long getInProgressJobs() { return inProgressJobs; }
        public void setInProgressJobs(long inProgressJobs) { this.inProgressJobs = inProgressJobs; }
        public long getCancelledJobs() { return cancelledJobs; }
        public void setCancelledJobs(long cancelledJobs) { this.cancelledJobs = cancelledJobs; }
        public BigDecimal getCompletionRate() { return completionRate; }
        public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }
        public BigDecimal getTotalLabourHours() { return totalLabourHours; }
        public void setTotalLabourHours(BigDecimal totalLabourHours) { this.totalLabourHours = totalLabourHours; }
        public BigDecimal getTotalBillableValue() { return totalBillableValue; }
        public void setTotalBillableValue(BigDecimal totalBillableValue) { this.totalBillableValue = totalBillableValue; }
        public BigDecimal getServiceHistoryRevenue() { return serviceHistoryRevenue; }
        public void setServiceHistoryRevenue(BigDecimal serviceHistoryRevenue) { this.serviceHistoryRevenue = serviceHistoryRevenue; }
        public long getCompletedHistoryCount() { return completedHistoryCount; }
        public void setCompletedHistoryCount(long completedHistoryCount) { this.completedHistoryCount = completedHistoryCount; }
        public BigDecimal getAverageCompletionHours() { return averageCompletionHours; }
        public void setAverageCompletionHours(BigDecimal averageCompletionHours) { this.averageCompletionHours = averageCompletionHours; }
    }

    public static class UserAuditActivityItem {
        private Long actorUserId;
        private long totalActions;
        private Map<String, Long> actionsByType;
        private Map<String, Long> actionsByEntity;
        private LocalDateTime lastActivityAt;

        public UserAuditActivityItem() {
        }

        public Long getActorUserId() { return actorUserId; }
        public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
        public long getTotalActions() { return totalActions; }
        public void setTotalActions(long totalActions) { this.totalActions = totalActions; }
        public Map<String, Long> getActionsByType() { return actionsByType; }
        public void setActionsByType(Map<String, Long> actionsByType) { this.actionsByType = actionsByType; }
        public Map<String, Long> getActionsByEntity() { return actionsByEntity; }
        public void setActionsByEntity(Map<String, Long> actionsByEntity) { this.actionsByEntity = actionsByEntity; }
        public LocalDateTime getLastActivityAt() { return lastActivityAt; }
        public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    }

    public static class EmployeeRosterSummary {
        private String employeeId;
        private String employeeCode;
        private String fullName;
        private String position;
        private BigDecimal paymentRate;
        private boolean active;

        public EmployeeRosterSummary() {
        }

        public EmployeeRosterSummary(
                String employeeId,
                String employeeCode,
                String fullName,
                String position,
                BigDecimal paymentRate,
                boolean active
        ) {
            this.employeeId = employeeId;
            this.employeeCode = employeeCode;
            this.fullName = fullName;
            this.position = position;
            this.paymentRate = paymentRate;
            this.active = active;
        }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getEmployeeCode() { return employeeCode; }
        public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public BigDecimal getPaymentRate() { return paymentRate; }
        public void setPaymentRate(BigDecimal paymentRate) { this.paymentRate = paymentRate; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public ReportMetadata getMetadata() { return metadata; }
    public void setMetadata(ReportMetadata metadata) { this.metadata = metadata; }
    public long getActiveEmployees() { return activeEmployees; }
    public void setActiveEmployees(long activeEmployees) { this.activeEmployees = activeEmployees; }
    public long getTotalEmployees() { return totalEmployees; }
    public void setTotalEmployees(long totalEmployees) { this.totalEmployees = totalEmployees; }
    public long getActiveTechniciansTracked() { return activeTechniciansTracked; }
    public void setActiveTechniciansTracked(long activeTechniciansTracked) { this.activeTechniciansTracked = activeTechniciansTracked; }
    public long getSystemActorsTracked() { return systemActorsTracked; }
    public void setSystemActorsTracked(long systemActorsTracked) { this.systemActorsTracked = systemActorsTracked; }
    public long getTotalAuditEventsInPeriod() { return totalAuditEventsInPeriod; }
    public void setTotalAuditEventsInPeriod(long totalAuditEventsInPeriod) { this.totalAuditEventsInPeriod = totalAuditEventsInPeriod; }
    public List<TechnicianPerformanceItem> getTechnicianPerformances() { return technicianPerformances; }
    public void setTechnicianPerformances(List<TechnicianPerformanceItem> technicianPerformances) { this.technicianPerformances = technicianPerformances; }
    public List<UserAuditActivityItem> getUserAuditActivities() { return userAuditActivities; }
    public void setUserAuditActivities(List<UserAuditActivityItem> userAuditActivities) { this.userAuditActivities = userAuditActivities; }
    public List<EmployeeRosterSummary> getEmployeeRoster() { return employeeRoster; }
    public void setEmployeeRoster(List<EmployeeRosterSummary> employeeRoster) { this.employeeRoster = employeeRoster; }
    public List<ChartSeriesDTO.ChartSegment> getEmployeePositionDistribution() { return employeePositionDistribution; }
    public void setEmployeePositionDistribution(List<ChartSeriesDTO.ChartSegment> employeePositionDistribution) { this.employeePositionDistribution = employeePositionDistribution; }
}