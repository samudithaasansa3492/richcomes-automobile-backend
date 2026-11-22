package com.richcomes.garage.history.controller;

import com.richcomes.garage.history.dto.ServiceHistoryCreateRequest;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.dto.ServiceHistoryUpdateRequest;
import com.richcomes.garage.history.service.ServiceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceHistoryControllerTest {

    @Mock
    private ServiceHistoryService serviceHistoryService;

    @InjectMocks
    private ServiceHistoryController controller;

    private ServiceHistoryCreateRequest validCreateRequest;
    private ServiceHistoryResponse sampleResponse;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        validCreateRequest = new ServiceHistoryCreateRequest();
        validCreateRequest.setJobId(100L);
        validCreateRequest.setVehicleId(10L);
        validCreateRequest.setServiceDate(LocalDate.of(2024, 1, 15));
        validCreateRequest.setOdometer(50000);
        validCreateRequest.setSummary("Regular service");
        validCreateRequest.setTechnicianId(5L);
        validCreateRequest.setInvoiceId(200L);
        validCreateRequest.setTotalCost(new BigDecimal("15000.00"));
        validCreateRequest.setNextServiceDate(LocalDate.of(2024, 7, 15));

        sampleResponse = new ServiceHistoryResponse();
        sampleResponse.setId(1L);
        sampleResponse.setJobId(100L);
        sampleResponse.setVehicleId(10L);
        sampleResponse.setServiceDate(LocalDate.of(2024, 1, 15));
        sampleResponse.setOdometer(50000);
        sampleResponse.setSummary("Regular service");
        sampleResponse.setTechnicianId(5L);
        sampleResponse.setInvoiceId(200L);
        sampleResponse.setTotalCost(new BigDecimal("15000.00"));
        sampleResponse.setNextServiceDate(LocalDate.of(2024, 7, 15));

        mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.1.1");

        // Set up authenticated user for tests that require it
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1", "password", List.of()));
    }

    // =====================================================
    // CREATE SERVICE HISTORY TESTS
    // =====================================================

    @Test
    void createServiceHistory_withValidRequest_returnsCreated() {
        when(serviceHistoryService.recordServiceHistory(any(ServiceHistoryCreateRequest.class)))
                .thenReturn(sampleResponse);

        ResponseEntity<ServiceHistoryResponse> response = controller.createServiceHistory(validCreateRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
    }

    // =====================================================
    // GET ALL HISTORIES TESTS
    // =====================================================

    @Test
    void getAllHistories_withDefaultParams_returnsPage() {
        Page<ServiceHistoryResponse> page = new PageImpl<>(List.of(sampleResponse));
        when(serviceHistoryService.getAllServiceHistories(0, 10)).thenReturn(page);

        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(0, 10, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getAllHistories_withDateRange_returnsFilteredPage() {
        Page<ServiceHistoryResponse> page = new PageImpl<>(List.of(sampleResponse));
        when(serviceHistoryService.getServiceHistoryByDateRange(any(), any(), eq(0), eq(10))).thenReturn(page);

        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(
                0, 10, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAllHistories_withInvalidPage_returnsBadRequest() {
        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(-1, 10, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getAllHistories_withInvalidSize_returnsBadRequest() {
        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(0, 101, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getAllHistories_withOnlyStartDate_returnsBadRequest() {
        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(
                0, 10, LocalDate.of(2024, 1, 1), null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getAllHistories_withOnlyEndDate_returnsBadRequest() {
        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(
                0, 10, null, LocalDate.of(2024, 12, 31));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getAllHistories_withStartAfterEnd_returnsBadRequest() {
        ResponseEntity<Page<ServiceHistoryResponse>> response = controller.getAllHistories(
                0, 10, LocalDate.of(2024, 12, 31), LocalDate.of(2024, 1, 1));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // =====================================================
    // GET BY ID TESTS
    // =====================================================

    @Test
    void getHistoryById_withExistingId_returnsOk() {
        when(serviceHistoryService.getServiceHistoryById(1L)).thenReturn(sampleResponse);

        ResponseEntity<ServiceHistoryResponse> response = controller.getHistoryById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getId());
    }

    // =====================================================
    // GET BY VEHICLE TESTS
    // =====================================================

    @Test
    void getHistoryByVehicle_returnsList() {
        when(serviceHistoryService.getServiceHistoryByVehicleId(10L)).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<ServiceHistoryResponse>> response = controller.getHistoryByVehicle(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // =====================================================
    // GET BY JOB TESTS
    // =====================================================

    @Test
    void getHistoryByJob_withExistingJob_returnsOk() {
        when(serviceHistoryService.getServiceHistoryByJobId(100L)).thenReturn(sampleResponse);

        ResponseEntity<ServiceHistoryResponse> response = controller.getHistoryByJob(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100L, response.getBody().getJobId());
    }

    // =====================================================
    // GET BY TECHNICIAN TESTS
    // =====================================================

    @Test
    void getHistoryByTechnician_returnsList() {
        when(serviceHistoryService.getServiceHistoryByTechnician(5L)).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<ServiceHistoryResponse>> response = controller.getHistoryByTechnician(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // =====================================================
    // UPDATE HISTORY TESTS
    // =====================================================

    @Test
    void updateHistory_withValidRequest_returnsOk() {
        ServiceHistoryUpdateRequest updateRequest = new ServiceHistoryUpdateRequest();
        updateRequest.setOdometer(55000);
        updateRequest.setSummary("Updated service");
        updateRequest.setTotalCost(new BigDecimal("16000.00"));

        when(serviceHistoryService.updateServiceHistory(eq(1L), any(ServiceHistoryUpdateRequest.class),
                eq(1L), eq("Correction"), eq("192.168.1.1"))).thenReturn(sampleResponse);

        ResponseEntity<ServiceHistoryResponse> response = controller.updateHistory(1L, updateRequest, "Correction", mockRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // =====================================================
    // DELETE HISTORY TESTS
    // =====================================================

    @Test
    void deleteHistory_withExistingId_returnsNoContent() {
        doNothing().when(serviceHistoryService).deleteServiceHistory(1L, 1L, "192.168.1.1");

        ResponseEntity<Void> response = controller.deleteHistory(1L, mockRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // =====================================================
    // TEST ENDPOINT
    // =====================================================

    @Test
    void test_returnsWorkingMessage() {
        ResponseEntity<String> response = controller.test();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Service History API Working!", response.getBody());
    }
}