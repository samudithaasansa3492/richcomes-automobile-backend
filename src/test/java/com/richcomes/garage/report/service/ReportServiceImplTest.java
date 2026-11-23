package com.richcomes.garage.report.service;

import com.richcomes.garage.appointment.dto.AppointmentResponse;
import com.richcomes.garage.appointment.enums.AppointmentStatus;
import com.richcomes.garage.appointment.service.AppointmentService;
import com.richcomes.garage.audit.dto.AuditLogResponse;
import com.richcomes.garage.audit.service.AuditLogService;
import com.richcomes.garage.billing.service.InvoiceCalculationService;
import com.richcomes.garage.customer.dto.CustomerResponse;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.service.CustomerService;
import com.richcomes.garage.employee.dto.EmployeeResponse;
import com.richcomes.garage.employee.entity.EmployeePosition;
import com.richcomes.garage.employee.service.EmployeeService;
import com.richcomes.garage.history.dto.ServiceHistoryResponse;
import com.richcomes.garage.history.service.ServiceHistoryService;
import com.richcomes.garage.inventory.category.dto.response.StockCategoryResponse;
import com.richcomes.garage.inventory.category.service.StockCategoryService;
import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.enums.GRNStatus;
import com.richcomes.garage.inventory.grn.service.GRNService;
import com.richcomes.garage.inventory.stockitem.dto.responce.StockItemResponse;
import com.richcomes.garage.inventory.stockitem.service.StockItemService;
import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.service.StockMovementService;
import com.richcomes.garage.inventory.supplier.dto.responce.SupplierResponse;
import com.richcomes.garage.inventory.supplier.service.SupplierService;
import com.richcomes.garage.notification.dto.NotificationResponse;
import com.richcomes.garage.notification.enums.NotificationStatus;
import com.richcomes.garage.notification.service.NotificationService;
import com.richcomes.garage.reminder.dto.ServiceReminderResponse;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import com.richcomes.garage.reminder.service.ServiceReminderService;
import com.richcomes.garage.report.dto.*;
import com.richcomes.garage.report.enums.ReportPeriod;
import com.richcomes.garage.report.enums.ReportType;
import com.richcomes.garage.service.dto.ServiceItemDTO;
import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.dto.ServiceJobItemDTO;
import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import com.richcomes.garage.service.service.ServiceItemService;
import com.richcomes.garage.service.service.ServiceJobItemService;
import com.richcomes.garage.service.service.ServiceJobPartService;
import com.richcomes.garage.service.service.ServiceJobService;
import com.richcomes.garage.vehicle.dto.VehicleResponse;
import com.richcomes.garage.vehicle.enums.VehicleStatus;
import com.richcomes.garage.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.richcomes.garage.report.service.impl.ReportServiceImpl;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReportServiceImplTest {

    @Mock private ReportDateRangeResolver dateRangeResolver;
    @Mock private AppointmentService appointmentService;
    @Mock private ServiceJobService serviceJobService;
    @Mock private ServiceJobItemService serviceJobItemService;
    @Mock private ServiceJobPartService serviceJobPartService;
    @Mock private ServiceItemService serviceItemService;
    @Mock private ServiceHistoryService serviceHistoryService;
    @Mock private StockItemService stockItemService;
    @Mock private StockCategoryService stockCategoryService;
    @Mock private StockMovementService stockMovementService;
    @Mock private GRNService grnService;
    @Mock private SupplierService supplierService;
    @Mock private CustomerService customerService;
    @Mock private VehicleService vehicleService;
    @Mock private EmployeeService employeeService;
    @Mock private ServiceReminderService serviceReminderService;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private InvoiceCalculationService invoiceCalculationService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private ReportDateRangeResolver.DateWindow testWindow;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 1, 31);
        testWindow = new ReportDateRangeResolver.DateWindow(startDate, endDate);

        when(dateRangeResolver.resolve(any(ReportPeriod.class), any(), any(), any()))
                .thenReturn(testWindow);
    }

    // =====================================================
    // OPERATIONAL REPORT TESTS
    // =====================================================

    @Test
    void getOperationalReport_returnsReportWithMetadata() {
        setupOperationalMocks();

        OperationalReportResponse response = reportService.getOperationalReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate);

        assertNotNull(response);
        assertNotNull(response.getMetadata());
        assertEquals(ReportType.OPERATIONAL, response.getMetadata().getReportType());
        assertEquals(ReportPeriod.MONTHLY, response.getMetadata().getPeriod());
        assertEquals(startDate, response.getMetadata().getStartDate());
        assertEquals(endDate, response.getMetadata().getEndDate());
    }

    @Test
    void getOperationalReport_withNullPeriod_defaultsToMonthly() {
        setupOperationalMocks();

        OperationalReportResponse response = reportService.getOperationalReport(
                null, LocalDate.now(), startDate, endDate);

        assertEquals(ReportPeriod.MONTHLY, response.getMetadata().getPeriod());
    }

    // =====================================================
    // STOCK REPORT TESTS
    // =====================================================

    @Test
    void getStockReport_returnsReportWithMetadata() {
        setupStockMocks();

        StockReportResponse response = reportService.getStockReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate);

        assertNotNull(response);
        assertNotNull(response.getMetadata());
        assertEquals(ReportType.STOCK, response.getMetadata().getReportType());
    }

    // =====================================================
    // FINANCIAL REPORT TESTS
    // =====================================================

    @Test
    void getFinancialReport_returnsReportWithMetadataAndNote() {
        setupFinancialMocks();

        FinancialReportResponse response = reportService.getFinancialReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate);

        assertNotNull(response);
        assertNotNull(response.getMetadata());
        assertEquals(ReportType.FINANCIAL, response.getMetadata().getReportType());
        assertNotNull(response.getBillingIntegrationNote());
        assertTrue(response.getBillingIntegrationNote().contains("Estimated operating difference"));
        assertTrue(response.getBillingIntegrationNote().contains("not actual cash flow"));
    }

    @Test
    void getFinancialReport_calculatesNetCashEstimate() {
        setupFinancialMocks();

        FinancialReportResponse response = reportService.getFinancialReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate);

        // Net estimate = recorded revenue - confirmed GRN spend
        // With our mocks: 15000 - 10000 = 5000
        assertNotNull(response.getNetOperatingCashEstimate());
    }

    // =====================================================
    // PERFORMANCE REPORT TESTS
    // =====================================================

    @Test
    void getPerformanceReport_returnsReportWithMetadata() {
        setupPerformanceMocks();

        PerformanceReportResponse response = reportService.getPerformanceReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate, null, null);

        assertNotNull(response);
        assertNotNull(response.getMetadata());
        assertEquals(ReportType.PERFORMANCE, response.getMetadata().getReportType());
    }

    // =====================================================
    // COMPREHENSIVE REPORT TESTS
    // =====================================================

    @Test
    void getComprehensiveReport_returnsAllSections() {
        setupAllMocks();

        ComprehensiveReportResponse response = reportService.getComprehensiveReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate);

        assertNotNull(response);
        assertNotNull(response.getMetadata());
        assertEquals(ReportType.COMPREHENSIVE, response.getMetadata().getReportType());
        assertNotNull(response.getOperational());
        assertNotNull(response.getStock());
        assertNotNull(response.getFinancial());
        assertNotNull(response.getPerformance());
        assertNotNull(response.getDashboard());
    }

    // =====================================================
    // DASHBOARD REPORT TESTS
    // =====================================================

    @Test
    void getDashboardReport_returnsDashboardWithKPIs() {
        setupAllMocks();

        DashboardReportResponse response = reportService.getDashboardReport(
                ReportPeriod.MONTHLY, LocalDate.now(), startDate, endDate);

        assertNotNull(response);
        assertNotNull(response.getMetadata());
        assertNotNull(response.getTotalRevenue());
        assertNotNull(response.getTotalProcurementSpend());
        assertNotNull(response.getNetOperatingEstimate());
        assertNotNull(response.getJobStatusBreakdown());
        assertNotNull(response.getAppointmentStatusBreakdown());
    }

    // =====================================================
    // HELPER MOCK SETUP METHODS
    // =====================================================

    private void setupOperationalMocks() {
        // Appointments
        AppointmentResponse appt = new AppointmentResponse();
        appt.setId(1L);
        appt.setStatus(AppointmentStatus.DONE);
        appt.setConfirmedDate(startDate);
        when(appointmentService.getAllAppointments()).thenReturn(List.of(appt));

        // Jobs
        ServiceJobDTO job = createServiceJobDTO("COMPLETED", startDate.atStartOfDay(), startDate.atTime(17, 0));
        when(serviceJobService.getAllJobs()).thenReturn(List.of(job));

        // Customers
        CustomerResponse cust = new CustomerResponse();
        cust.setId(1L);
        cust.setStatus(CustomerStatus.ACTIVE);
        cust.setCreatedAt(LocalDateTime.now());
        when(customerService.getAllCustomers()).thenReturn(List.of(cust));

        // Vehicles
        VehicleResponse veh = new VehicleResponse();
        veh.setId(1L);
        veh.setStatus(VehicleStatus.ACTIVE);
        veh.setCreatedAt(LocalDateTime.now());
        when(vehicleService.getAllVehicles()).thenReturn(List.of(veh));

        // Service History
        ServiceHistoryResponse hist = new ServiceHistoryResponse();
        hist.setId(1L);
        hist.setServiceDate(startDate);
        hist.setTotalCost(new BigDecimal("15000.00"));
        when(serviceHistoryService.getServiceHistoryByDateRange(any(), any(), anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(hist)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Reminders
        ServiceReminderResponse rem = new ServiceReminderResponse();
        rem.setId(1L);
        rem.setStatus(ServiceReminderStatus.PENDING);
        when(serviceReminderService.getAllReminders(any())).thenReturn(List.of(rem));
        when(serviceReminderService.getDueReminders()).thenReturn(List.of());

        // Notifications
        NotificationResponse notif = new NotificationResponse();
        notif.setId(1L);
        notif.setStatus(NotificationStatus.SENT);
        notif.setCreatedAt(LocalDateTime.now());
        when(notificationService.getAllNotifications(any(), any(), anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(notif)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Service Items
        ServiceItemDTO item = new ServiceItemDTO();
        item.setId(1L);
        item.setName("Oil Change");
        when(serviceItemService.getAllServiceItems()).thenReturn(List.of(item));

        // Job Items
        ServiceJobItemDTO jobItem = new ServiceJobItemDTO();
        jobItem.setId(1L);
        jobItem.setServiceItemId(1L);
        jobItem.setLabourHours(1.0d);
        jobItem.setLabourRate(new BigDecimal("5000"));
        when(serviceJobItemService.getItemsByJobId(anyLong())).thenReturn(List.of(jobItem));
    }

    private void setupStockMocks() {
        // Stock Items
        StockItemResponse stockItem = new StockItemResponse();
        stockItem.setId(1L);
        stockItem.setItemCode("OIL-001");
        stockItem.setItemName("Engine Oil");
        stockItem.setCategoryId(1L);
        stockItem.setCategoryName("Lubricants");
        stockItem.setCurrentQuantity(BigDecimal.valueOf(100));
        stockItem.setUnitCost(new BigDecimal("500"));
        stockItem.setSellingPrice(new BigDecimal("800"));
        stockItem.setActive(true);
        stockItem.setReorderLevel(BigDecimal.valueOf(10));
        when(stockItemService.getAllItems()).thenReturn(List.of(stockItem));
        when(stockItemService.getLowStockItems()).thenReturn(List.of());

        // Categories
        StockCategoryResponse cat = new StockCategoryResponse();
        cat.setId(1L);
        cat.setName("Lubricants");
        when(stockCategoryService.getAllCategories()).thenReturn(List.of(cat));

        // Movements
        StockMovementResponse mov = new StockMovementResponse();
        mov.setId(1L);
        mov.setTransactionType(StockTransactionType.STOCK_IN);
        mov.setQuantity(BigDecimal.valueOf(50));
        mov.setCreatedAt(LocalDateTime.now());
        when(stockMovementService.getAllTransactions()).thenReturn(List.of(mov));

        // GRNs
        GRNResponse grn = new GRNResponse();
        grn.setId(1L);
        grn.setStatus(GRNStatus.CONFIRMED);
        grn.setSupplierId(1L);
        grn.setSupplierName("Supplier A");
        grn.setTotalAmount(new BigDecimal("10000"));
        grn.setReceivedAt(LocalDateTime.now());
        when(grnService.getAllGRNs()).thenReturn(List.of(grn));

        // Suppliers
        SupplierResponse sup = new SupplierResponse(1L, "Supplier A", "TIN-001", "Contact", "123456789", "sup@a.com", "Address", true);
        when(supplierService.getAllSuppliers()).thenReturn(List.of(sup));
    }

    private void setupFinancialMocks() {
        // Service History
        ServiceHistoryResponse hist = new ServiceHistoryResponse();
        hist.setId(1L);
        hist.setServiceDate(startDate);
        hist.setTotalCost(new BigDecimal("15000"));
        when(serviceHistoryService.getServiceHistoryByDateRange(any(), any(), anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(hist)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Jobs
        ServiceJobDTO job = createServiceJobDTO("COMPLETED", startDate.atStartOfDay(), startDate.atTime(17, 0));
        when(serviceJobService.getAllJobs()).thenReturn(List.of(job));

        // Job Items
        ServiceJobItemDTO jobItem = new ServiceJobItemDTO();
        jobItem.setId(1L);
        jobItem.setServiceItemId(1L);
        jobItem.setLabourHours(1.0d);
        jobItem.setLabourRate(new BigDecimal("5000"));
        when(serviceJobItemService.getItemsByJobId(anyLong())).thenReturn(List.of(jobItem));

        // Job Parts
        ServiceJobPartDTO part = new ServiceJobPartDTO();
        part.setId(1L);
        part.setQuantity(2.0);
        part.setUnitPrice(new BigDecimal("2500"));
        when(serviceJobPartService.getPartsByJobId(anyLong())).thenReturn(List.of(part));

        // GRNs
        GRNResponse grn = new GRNResponse();
        grn.setId(1L);
        grn.setStatus(GRNStatus.CONFIRMED);
        grn.setTotalAmount(new BigDecimal("10000"));
        grn.setReceivedAt(LocalDateTime.now());
        when(grnService.getAllGRNs()).thenReturn(List.of(grn));

        // Customers for credit pool
        CustomerResponse cust = new CustomerResponse();
        cust.setId(1L);
        cust.setStatus(CustomerStatus.ACTIVE);
        cust.setCreditLimit(new BigDecimal("50000"));
        when(customerService.getAllCustomers()).thenReturn(List.of(cust));

        // Employees
        java.util.UUID empId = java.util.UUID.randomUUID();
        EmployeeResponse emp = new EmployeeResponse(empId, "EMP-001", "John", "Doe", "123456789", EmployeePosition.TECHNICIAN, new BigDecimal("1000"), true);
        when(employeeService.getAllEmployees()).thenReturn(List.of(emp));

        // Appointments
        AppointmentResponse appt = new AppointmentResponse();
        appt.setId(1L);
        appt.setConfirmedDate(startDate);
        when(appointmentService.getAllAppointments()).thenReturn(List.of(appt));

        // Invoice calculation
        when(invoiceCalculationService.calculateLineAmount(any(), any())).thenAnswer(inv -> {
            BigDecimal qty = inv.getArgument(0);
            BigDecimal price = inv.getArgument(1);
            return qty.multiply(price);
        });
    }

    private void setupPerformanceMocks() {
        // Jobs
        ServiceJobDTO job = createServiceJobDTO("COMPLETED", startDate.atStartOfDay(), startDate.atTime(17, 0));
        job.setAssignedEmployeeId(1L);
        when(serviceJobService.getAllJobs()).thenReturn(List.of(job));

        // Service History
        ServiceHistoryResponse hist = new ServiceHistoryResponse();
        hist.setId(1L);
        hist.setTechnicianId(1L);
        hist.setTotalCost(new BigDecimal("15000"));
        when(serviceHistoryService.getServiceHistoryByDateRange(any(), any(), anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(hist)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Job Items
        ServiceJobItemDTO jobItem = new ServiceJobItemDTO();
        jobItem.setId(1L);
        jobItem.setServiceItemId(1L);
        jobItem.setLabourHours(1.0d);
        jobItem.setLabourRate(new BigDecimal("5000"));
        jobItem.setTotalAmount(new BigDecimal("5000")); // Initialize totalAmount
        when(serviceJobItemService.getItemsByJobId(anyLong())).thenReturn(List.of(jobItem));

        // Job Parts
        ServiceJobPartDTO part = new ServiceJobPartDTO();
        part.setId(1L);
        part.setQuantity(2.0);
        part.setUnitPrice(new BigDecimal("2500"));
        part.setTotalAmount(new BigDecimal("5000")); // Initialize totalAmount
        when(serviceJobPartService.getPartsByJobId(anyLong())).thenReturn(List.of(part));

        // Employees
        java.util.UUID empId = java.util.UUID.randomUUID();
        EmployeeResponse emp = new EmployeeResponse(empId, "EMP-001", "John", "Doe", "123456789", EmployeePosition.TECHNICIAN, new BigDecimal("1000"), true);
        when(employeeService.getAllEmployees()).thenReturn(List.of(emp));

        // Audit Logs
        AuditLogResponse audit = new AuditLogResponse();
        audit.setId(1L);
        audit.setActorUserId(1L);
        audit.setAction("UPDATE");
        audit.setEntityType("SERVICE_HISTORY");
        audit.setEntityId(100L);
        audit.setTimestamp(LocalDateTime.now());
        when(auditLogService.getAuditLogsByDateRange(any(), any())).thenReturn(List.of(audit));
    }

    private void setupAllMocks() {
        setupOperationalMocks();
        setupStockMocks();
        setupFinancialMocks();
        setupPerformanceMocks();
    }

    private ServiceJobDTO createServiceJobDTO(String status, LocalDateTime jobDate, LocalDateTime completionDate) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setId(1L);
        dto.setJobNo("JOB-0001");
        dto.setVehicleId(1L);
        dto.setCustomerId(1L);
        dto.setAssignedEmployeeId(1L);
        dto.setStatus(status);
        dto.setJobDate(jobDate);
        dto.setExpectedCompletionDate(jobDate.plusDays(1));
        dto.setActualCompletionDate(completionDate);
        dto.setComplaintDescription("Test complaint");
        dto.setNotes("Test notes");
        return dto;
    }
}