package com.richcomes.garage.service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_jobs")
public class ServiceJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_no", unique = true, nullable = false)
    private String jobNo;

    // අනිත් සාමාජිකයන්ගේ tables වලට සම්බන්ධ වන IDs
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "assigned_employee_id")
    private Long assignedEmployeeId;

    // Job එකේ වර්තමාන තත්ත්වය (Status)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "job_date")
    private LocalDateTime jobDate;

    @Column(name = "expected_completion_date")
    private LocalDateTime expectedCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDateTime actualCompletionDate;

    @Column(name = "complaint_description", columnDefinition = "TEXT")
    private String complaintDescription;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Default Constructor
    public ServiceJob() {
    }

    // JobStatus Enum (තත්ත්වයන් ලැයිස්තුව)
    public enum JobStatus {
        PENDING, IN_PROGRESS, COMPLETED, CLOSED, CANCELLED
    }

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

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

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