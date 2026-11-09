package com.richcomes.garage.service.dto;

import java.time.LocalDateTime;

public class ServiceJobDTO {
    private Long id;
    private String jobNo;
    private Long vehicleId;
    private Long customerId;
    private Long branchId;
    private Long assignedEmployeeId;
    private String status;
    private LocalDateTime jobDate;
    private LocalDateTime expectedCompletionDate;
    private LocalDateTime actualCompletionDate;
    private String complaintDescription;
    private String notes;

    public ServiceJobDTO() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobNo() { return jobNo; }
    public void setJobNo(String jobNo) { this.jobNo = jobNo; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getAssignedEmployeeId() { return assignedEmployeeId; }
    public void setAssignedEmployeeId(Long assignedEmployeeId) { this.assignedEmployeeId = assignedEmployeeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getJobDate() { return jobDate; }
    public void setJobDate(LocalDateTime jobDate) { this.jobDate = jobDate; }
    public LocalDateTime getExpectedCompletionDate() { return expectedCompletionDate; }
    public void setExpectedCompletionDate(LocalDateTime expectedCompletionDate) { this.expectedCompletionDate = expectedCompletionDate; }
    public LocalDateTime getActualCompletionDate() { return actualCompletionDate; }
    public void setActualCompletionDate(LocalDateTime actualCompletionDate) { this.actualCompletionDate = actualCompletionDate; }
    public String getComplaintDescription() { return complaintDescription; }
    public void setComplaintDescription(String complaintDescription) { this.complaintDescription = complaintDescription; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}