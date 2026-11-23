package com.richcomes.garage.service.service.impl;

import com.richcomes.garage.history.integration.JobCompletionHistoryAdapter;
import com.richcomes.garage.history.integration.JobCompletionHistoryAdapter.JobCompletionRequest;
import com.richcomes.garage.history.service.ServiceHistoryService;
import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.repository.ServiceJobRepository;
import com.richcomes.garage.service.service.ServiceJobService;
import com.richcomes.garage.vehicle.entity.Vehicle;
import com.richcomes.garage.vehicle.repository.VehicleRepository;
import com.richcomes.garage.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceJobServiceImplIntegrationTest {

    @Mock
    private ServiceJobRepository repository;

    @Mock
    private VehicleService vehicleService;

    @Mock
    private JobCompletionHistoryAdapter historyAdapter;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private ServiceJobServiceImpl serviceJobService;

    private ServiceJob completedJob;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        // Create test vehicle
        testVehicle = new Vehicle();
        ReflectionTestUtils.setField(testVehicle, "id", 10L);
        ReflectionTestUtils.setField(testVehicle, "currentMileage", 50000.0);

        // Create completed job
        completedJob = new ServiceJob();
        ReflectionTestUtils.setField(completedJob, "id", 100L);
        ReflectionTestUtils.setField(completedJob, "jobNo", "JOB-0001");
        ReflectionTestUtils.setField(completedJob, "vehicleId", 10L);
        ReflectionTestUtils.setField(completedJob, "customerId", 1L);
        ReflectionTestUtils.setField(completedJob, "assignedEmployeeId", 5L);
        ReflectionTestUtils.setField(completedJob, "status", ServiceJob.JobStatus.COMPLETED);
        ReflectionTestUtils.setField(completedJob, "jobDate", LocalDateTime.of(2024, 1, 15, 9, 0));
        ReflectionTestUtils.setField(completedJob, "actualCompletionDate", LocalDateTime.of(2024, 1, 15, 17, 0));
        ReflectionTestUtils.setField(completedJob, "complaintDescription", "Oil change and filter replacement");
        ReflectionTestUtils.setField(completedJob, "notes", "Customer requested synthetic oil");
    }

    @Test
    void updateJobStatus_toCompleted_createsServiceHistory() {
        // Given
        when(repository.findById(100L)).thenReturn(Optional.of(completedJob));
        when(repository.save(any(ServiceJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleService.getVehicleEntityById(10L)).thenReturn(testVehicle);
        when(historyAdapter.createHistoryFromJobCompletion(any(JobCompletionRequest.class)))
                .thenReturn(new com.richcomes.garage.history.dto.ServiceHistoryResponse());

        // When
        ServiceJobDTO result = serviceJobService.updateJobStatus(100L, ServiceJob.JobStatus.COMPLETED);

        // Then
        assertEquals(ServiceJob.JobStatus.COMPLETED.name(), result.getStatus());
        assertNotNull(result.getActualCompletionDate());

        // Verify history adapter was called with correct data
        ArgumentCaptor<JobCompletionRequest> captor = ArgumentCaptor.forClass(JobCompletionRequest.class);
        verify(historyAdapter).createHistoryFromJobCompletion(captor.capture());

        JobCompletionRequest request = captor.getValue();
        assertEquals(100L, request.getJobId());
        assertEquals(10L, request.getVehicleId());
        assertEquals(5L, request.getTechnicianId());
        assertEquals("Job completed: Oil change and filter replacement", request.getSummary());
        assertEquals(50000, request.getOdometer());
        assertNotNull(request.getServiceDate());
        assertEquals(BigDecimal.ZERO, request.getTotalCost());
    }

    @Test
    void updateJobStatus_toClosed_createsServiceHistory() {
        // Given
        when(repository.findById(100L)).thenReturn(Optional.of(completedJob));
        when(repository.save(any(ServiceJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleService.getVehicleEntityById(10L)).thenReturn(testVehicle);
        when(historyAdapter.createHistoryFromJobCompletion(any(JobCompletionRequest.class)))
                .thenReturn(new com.richcomes.garage.history.dto.ServiceHistoryResponse());

        // When
        ServiceJobDTO result = serviceJobService.updateJobStatus(100L, ServiceJob.JobStatus.CLOSED);

        // Then
        assertEquals(ServiceJob.JobStatus.CLOSED.name(), result.getStatus());
        verify(historyAdapter).createHistoryFromJobCompletion(any(JobCompletionRequest.class));
    }

    @Test
    void updateJobStatus_toInProgress_doesNotCreateHistory() {
        // Given
        when(repository.findById(100L)).thenReturn(Optional.of(completedJob));
        when(repository.save(any(ServiceJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ServiceJobDTO result = serviceJobService.updateJobStatus(100L, ServiceJob.JobStatus.IN_PROGRESS);

        // Then
        assertEquals(ServiceJob.JobStatus.IN_PROGRESS.name(), result.getStatus());
        verify(historyAdapter, never()).createHistoryFromJobCompletion(any());
    }

    @Test
    void updateJobStatus_toCancelled_doesNotCreateHistory() {
        // Given
        when(repository.findById(100L)).thenReturn(Optional.of(completedJob));
        when(repository.save(any(ServiceJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ServiceJobDTO result = serviceJobService.updateJobStatus(100L, ServiceJob.JobStatus.CANCELLED);

        // Then
        assertEquals(ServiceJob.JobStatus.CANCELLED.name(), result.getStatus());
        verify(historyAdapter, never()).createHistoryFromJobCompletion(any());
    }

    @Test
    void updateJobStatus_withHistoryAdapterFailure_logsErrorAndContinues() {
        // Given
        when(repository.findById(100L)).thenReturn(Optional.of(completedJob));
        when(repository.save(any(ServiceJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleService.getVehicleEntityById(10L)).thenReturn(testVehicle);
        when(historyAdapter.createHistoryFromJobCompletion(any(JobCompletionRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid odometer"));

        // When
        ServiceJobDTO result = serviceJobService.updateJobStatus(100L, ServiceJob.JobStatus.COMPLETED);

        // Then - job status change succeeds even if history creation fails
        assertEquals(ServiceJob.JobStatus.COMPLETED.name(), result.getStatus());
    }

    @Test
    void updateJobStatus_toCompleted_withoutVehicleMileage_usesZeroOdometer() {
        // Given
        Vehicle vehicleNoMileage = new Vehicle();
        ReflectionTestUtils.setField(vehicleNoMileage, "id", 10L);
        ReflectionTestUtils.setField(vehicleNoMileage, "currentMileage", null);

        when(repository.findById(100L)).thenReturn(Optional.of(completedJob));
        when(repository.save(any(ServiceJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleService.getVehicleEntityById(10L)).thenReturn(vehicleNoMileage);
        when(historyAdapter.createHistoryFromJobCompletion(any(JobCompletionRequest.class)))
                .thenReturn(new com.richcomes.garage.history.dto.ServiceHistoryResponse());

        // When
        ServiceJobDTO result = serviceJobService.updateJobStatus(100L, ServiceJob.JobStatus.COMPLETED);

        // Then
        assertEquals(ServiceJob.JobStatus.COMPLETED.name(), result.getStatus());
        ArgumentCaptor<JobCompletionRequest> captor = ArgumentCaptor.forClass(JobCompletionRequest.class);
        verify(historyAdapter).createHistoryFromJobCompletion(captor.capture());
        assertEquals(0, captor.getValue().getOdometer());
    }
}