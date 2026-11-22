package com.richcomes.garage.history.integration;

import com.richcomes.garage.history.dto.ServiceHistoryCreateRequest;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.service.ServiceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class JobCompletionHistoryAdapterTest {

    @Mock
    private ServiceHistoryService serviceHistoryService;

    @InjectMocks
    private JobCompletionHistoryAdapter adapter;

    private JobCompletionHistoryAdapter.JobCompletionRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new JobCompletionHistoryAdapter.JobCompletionRequest();
        validRequest.setJobId(100L);
        validRequest.setVehicleId(10L);
        validRequest.setServiceDate(LocalDate.of(2024, 1, 15));
        validRequest.setOdometer(50000);
        validRequest.setSummary("Regular service - oil change, filter replacement");
        validRequest.setTechnicianId(5L);
        validRequest.setInvoiceId(200L);
        validRequest.setTotalCost(new BigDecimal("15000.00"));
        validRequest.setNextServiceDate(LocalDate.of(2024, 7, 15));
    }

    @Test
    void createHistoryFromJobCompletion_withValidRequest_createsHistory() {
        ServiceHistoryResponse expectedResponse = new ServiceHistoryResponse();
        expectedResponse.setId(1L);
        expectedResponse.setJobId(100L);
        expectedResponse.setVehicleId(10L);
        expectedResponse.setServiceDate(LocalDate.of(2024, 1, 15));
        expectedResponse.setOdometer(50000);
        expectedResponse.setSummary("Regular service - oil change, filter replacement");
        expectedResponse.setTechnicianId(5L);
        expectedResponse.setInvoiceId(200L);
        expectedResponse.setTotalCost(new BigDecimal("15000.00"));
        expectedResponse.setNextServiceDate(LocalDate.of(2024, 7, 15));

        when(serviceHistoryService.recordServiceHistory(any(ServiceHistoryCreateRequest.class)))
                .thenReturn(expectedResponse);

        ServiceHistoryResponse response = adapter.createHistoryFromJobCompletion(validRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(100L, response.getJobId());
        verify(serviceHistoryService).recordServiceHistory(any(ServiceHistoryCreateRequest.class));
    }

    @Test
    void createHistoryFromJobCompletion_withNullRequest_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(null));

        assertEquals("Job completion request is required", ex.getMessage());
        verify(serviceHistoryService, never()).recordServiceHistory(any());
    }

    @Test
    void createHistoryFromJobCompletion_withNullJobId_throwsException() {
        validRequest.setJobId(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid job ID is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withZeroJobId_throwsException() {
        validRequest.setJobId(0L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid job ID is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNegativeJobId_throwsException() {
        validRequest.setJobId(-1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid job ID is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNullVehicleId_throwsException() {
        validRequest.setVehicleId(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid vehicle ID is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNullServiceDate_throwsException() {
        validRequest.setServiceDate(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Service date is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNegativeOdometer_throwsException() {
        validRequest.setOdometer(-1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid odometer reading is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withBlankSummary_throwsException() {
        validRequest.setSummary("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Service summary is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNullTechnicianId_throwsException() {
        validRequest.setTechnicianId(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid technician ID is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNegativeTotalCost_throwsException() {
        validRequest.setTotalCost(new BigDecimal("-100"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Valid total cost is required", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNextServiceDateBeforeServiceDate_throwsException() {
        validRequest.setNextServiceDate(LocalDate.of(2024, 1, 10));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.createHistoryFromJobCompletion(validRequest));

        assertEquals("Next service date cannot be before service date", ex.getMessage());
    }

    @Test
    void createHistoryFromJobCompletion_withNextServiceDateEqualToServiceDate_allowed() {
        validRequest.setNextServiceDate(LocalDate.of(2024, 1, 15));

        ServiceHistoryResponse expectedResponse = new ServiceHistoryResponse();
        expectedResponse.setId(1L);
        when(serviceHistoryService.recordServiceHistory(any(ServiceHistoryCreateRequest.class)))
                .thenReturn(expectedResponse);

        ServiceHistoryResponse response = adapter.createHistoryFromJobCompletion(validRequest);

        assertNotNull(response);
    }

    @Test
    void createHistoryFromJobCompletion_withNullNextServiceDate_allowed() {
        validRequest.setNextServiceDate(null);

        ServiceHistoryResponse expectedResponse = new ServiceHistoryResponse();
        expectedResponse.setId(1L);
        when(serviceHistoryService.recordServiceHistory(any(ServiceHistoryCreateRequest.class)))
                .thenReturn(expectedResponse);

        ServiceHistoryResponse response = adapter.createHistoryFromJobCompletion(validRequest);

        assertNotNull(response);
    }

    @Test
    void createHistoryFromJobCompletion_mapsAllFieldsCorrectly() {
        ServiceHistoryResponse expectedResponse = new ServiceHistoryResponse();
        expectedResponse.setId(1L);
        when(serviceHistoryService.recordServiceHistory(any(ServiceHistoryCreateRequest.class)))
                .thenReturn(expectedResponse);

        adapter.createHistoryFromJobCompletion(validRequest);

        ArgumentCaptor<ServiceHistoryCreateRequest> captor = ArgumentCaptor.forClass(ServiceHistoryCreateRequest.class);
        verify(serviceHistoryService).recordServiceHistory(captor.capture());

        ServiceHistoryCreateRequest request = captor.getValue();
        assertEquals(100L, request.getJobId());
        assertEquals(10L, request.getVehicleId());
        assertEquals(LocalDate.of(2024, 1, 15), request.getServiceDate());
        assertEquals(50000, request.getOdometer());
        assertEquals("Regular service - oil change, filter replacement", request.getSummary());
        assertEquals(5L, request.getTechnicianId());
        assertEquals(200L, request.getInvoiceId());
        assertEquals(new BigDecimal("15000.00"), request.getTotalCost());
        assertEquals(LocalDate.of(2024, 7, 15), request.getNextServiceDate());
    }
}