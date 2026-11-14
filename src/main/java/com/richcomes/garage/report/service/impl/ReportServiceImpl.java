package com.richcomes.garage.report.service.impl;

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
import com.richcomes.garage.report.dto.ChartSeriesDTO;
import com.richcomes.garage.report.dto.ComprehensiveReportResponse;
import com.richcomes.garage.report.dto.DashboardReportResponse;
import com.richcomes.garage.report.dto.FinancialReportResponse;
import com.richcomes.garage.report.dto.OperationalReportResponse;
import com.richcomes.garage.report.dto.PerformanceReportResponse;
import com.richcomes.garage.report.dto.ReportMetadata;
import com.richcomes.garage.report.dto.StockReportResponse;
import com.richcomes.garage.report.enums.ReportPeriod;
import com.richcomes.garage.report.enums.ReportType;
import com.richcomes.garage.report.service.ReportDateRangeResolver;
import com.richcomes.garage.report.service.ReportService;
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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final int PAGE_FETCH_SIZE = 100;
    private static final String CURRENCY = "LKR";

    private final ReportDateRangeResolver dateRangeResolver;
    private final AppointmentService appointmentService;
    private final ServiceJobService serviceJobService;
    private final ServiceJobItemService serviceJobItemService;
    private final ServiceJobPartService serviceJobPartService;
    private final ServiceItemService serviceItemService;
    private final ServiceHistoryService serviceHistoryService;
    private final StockItemService stockItemService;
    private final StockCategoryService stockCategoryService;
    private final StockMovementService stockMovementService;
    private final GRNService grnService;
    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final EmployeeService employeeService;
    private final ServiceReminderService serviceReminderService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final InvoiceCalculationService invoiceCalculationService;

    public ReportServiceImpl(
            ReportDateRangeResolver dateRangeResolver,
            AppointmentService appointmentService,
            ServiceJobService serviceJobService,
            ServiceJobItemService serviceJobItemService,
            ServiceJobPartService serviceJobPartService,
            ServiceItemService serviceItemService,
            ServiceHistoryService serviceHistoryService,
            StockItemService stockItemService,
            StockCategoryService stockCategoryService,
            StockMovementService stockMovementService,
            GRNService grnService,
            SupplierService supplierService,
            CustomerService customerService,
            VehicleService vehicleService,
            EmployeeService employeeService,
            ServiceReminderService serviceReminderService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            InvoiceCalculationService invoiceCalculationService
    ) {
        this.dateRangeResolver = dateRangeResolver;
        this.appointmentService = appointmentService;
        this.serviceJobService = serviceJobService;
        this.serviceJobItemService = serviceJobItemService;
        this.serviceJobPartService = serviceJobPartService;
        this.serviceItemService = serviceItemService;
        this.serviceHistoryService = serviceHistoryService;
        this.stockItemService = stockItemService;
        this.stockCategoryService = stockCategoryService;
        this.stockMovementService = stockMovementService;
        this.grnService = grnService;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.employeeService = employeeService;
        this.serviceReminderService = serviceReminderService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.invoiceCalculationService = invoiceCalculationService;
    }

    @Override
    
public DashboardReportResponse getDashboardReport(
        ReportPeriod period,
        LocalDate referenceDate,
        LocalDate startDate,
        LocalDate endDate
) {
    ReportPeriod effectivePeriod =
            period != null ? period : ReportPeriod.MONTHLY;

    ReportDateRangeResolver.DateWindow window =
            dateRangeResolver.resolve(
                    effectivePeriod,
                    referenceDate,
                    startDate,
                    endDate
            );

    OperationalReportResponse operational =
            buildOperationalReport(effectivePeriod, window);

    StockReportResponse stock =
            buildStockReport(effectivePeriod, window);

    FinancialReportResponse financial =
            buildFinancialReport(effectivePeriod, window);

    PerformanceReportResponse performance =
            buildPerformanceReport(
                    effectivePeriod, window, null, null
            );

    return assembleDashboard(
            effectivePeriod,
            window,
            operational,
            stock,
            financial,
            performance
    );
}

    @Override
    public OperationalReportResponse getOperationalReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        ReportPeriod effectivePeriod = period != null ? period : ReportPeriod.MONTHLY;
        ReportDateRangeResolver.DateWindow window =
                dateRangeResolver.resolve(effectivePeriod, referenceDate, startDate, endDate);
        return buildOperationalReport(effectivePeriod, window);
    }

    @Override
    public StockReportResponse getStockReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        ReportPeriod effectivePeriod = period != null ? period : ReportPeriod.MONTHLY;
        ReportDateRangeResolver.DateWindow window =
                dateRangeResolver.resolve(effectivePeriod, referenceDate, startDate, endDate);
        return buildStockReport(effectivePeriod, window);
    }

    @Override
    public FinancialReportResponse getFinancialReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        ReportPeriod effectivePeriod = period != null ? period : ReportPeriod.MONTHLY;
        ReportDateRangeResolver.DateWindow window =
                dateRangeResolver.resolve(effectivePeriod, referenceDate, startDate, endDate);
        return buildFinancialReport(effectivePeriod, window);
    }

    @Override
    public PerformanceReportResponse getPerformanceReport(
            ReportPeriod period,
            LocalDate referenceDate,
            LocalDate startDate,
            LocalDate endDate,
            Long technicianId,
            Long actorUserId
    ) {
        ReportPeriod effectivePeriod = period != null ? period : ReportPeriod.MONTHLY;
        ReportDateRangeResolver.DateWindow window =
                dateRangeResolver.resolve(effectivePeriod, referenceDate, startDate, endDate);
        return buildPerformanceReport(effectivePeriod, window, technicianId, actorUserId);
    }

    @Override
public ComprehensiveReportResponse getComprehensiveReport(
        ReportPeriod period,
        LocalDate referenceDate,
        LocalDate startDate,
        LocalDate endDate
) {
    ReportPeriod effectivePeriod =
            period != null ? period : ReportPeriod.MONTHLY;

    ReportDateRangeResolver.DateWindow window =
            dateRangeResolver.resolve(
                    effectivePeriod,
                    referenceDate,
                    startDate,
                    endDate
            );

    OperationalReportResponse operational =
            buildOperationalReport(effectivePeriod, window);

    StockReportResponse stock =
            buildStockReport(effectivePeriod, window);

    FinancialReportResponse financial =
            buildFinancialReport(effectivePeriod, window);

    PerformanceReportResponse performance =
            buildPerformanceReport(
                    effectivePeriod, window, null, null
            );

    ComprehensiveReportResponse response =
            new ComprehensiveReportResponse();

    response.setMetadata(
            createMetadata(
                    ReportType.COMPREHENSIVE,
                    effectivePeriod,
                    window
            )
    );

    response.setOperational(operational);
    response.setStock(stock);
    response.setFinancial(financial);
    response.setPerformance(performance);

    response.setDashboard(
            assembleDashboard(
                    effectivePeriod,
                    window,
                    operational,
                    stock,
                    financial,
                    performance
            )
    );

    return response;
}

        private DashboardReportResponse assembleDashboard(
        ReportPeriod period,
        ReportDateRangeResolver.DateWindow window,
        OperationalReportResponse operational,
        StockReportResponse stock,
        FinancialReportResponse financial,
        PerformanceReportResponse performance
) {
    List<NotificationResponse> notificationsInWindow =
            fetchNotificationsInRange(
                    window.startDate(),
                    window.endDate()
            );

    DashboardReportResponse dashboard =
            new DashboardReportResponse();

    dashboard.setMetadata(
            createMetadata(
                    ReportType.COMPREHENSIVE,
                    period,
                    window
            )
    );

    dashboard.setTotalRevenue(
            financial.getRecordedServiceRevenue());

    dashboard.setTotalProcurementSpend(
            financial.getConfirmedGrnProcurementSpend());

    dashboard.setNetOperatingEstimate(
            financial.getNetOperatingCashEstimate());

    dashboard.setInventoryValuation(
            stock.getTotalInventoryCostValue());

    dashboard.setTotalJobs(
            operational.getTotalJobs());

    dashboard.setCompletedJobs(
            operational.getCompletedJobs()
                    + operational.getClosedJobs());

    dashboard.setJobCompletionRate(
            operational.getJobCompletionRate());

    dashboard.setTotalAppointments(
            operational.getTotalAppointments());

    dashboard.setPendingAppointments(
            operational.getPendingAppointments());

    dashboard.setActiveCustomers(
            operational.getActiveCustomers());

    dashboard.setActiveVehicles(
            operational.getActiveVehicles());

    dashboard.setLowStockAlerts(
            stock.getLowStockCount());

    dashboard.setDueReminders(
            operational.getDueRemindersCount());

    dashboard.setFailedNotifications(
            operational.getFailedNotificationsInPeriod());

    dashboard.setRevenueAndOperationsTrend(
            financial.getFinancialTrend());

    dashboard.setJobStatusBreakdown(
            operational.getJobStatusDistribution());

    dashboard.setAppointmentStatusBreakdown(
            operational.getAppointmentStatusDistribution());

    dashboard.setRevenueComposition(
            financial.getRevenueComposition());

    dashboard.setStockValueByCategory(
            buildCategoryValuationSegments(
                    stock.getCategoryBreakdown()
            )
    );

    dashboard.setNotificationStatusDistribution(
        buildNotificationSegments(notificationsInWindow)
);

    dashboard.setTopTechnicians(
            performance.getTechnicianPerformances()
                    .stream()
                    .limit(5)
                    .toList()
    );

    return dashboard;
}

    // =====================================================
    // OPERATIONAL REPORT BUILDER
    // =====================================================

    private OperationalReportResponse buildOperationalReport(
            ReportPeriod period,
            ReportDateRangeResolver.DateWindow window
    ) {
        List<AppointmentResponse> appointments = appointmentService.getAllAppointments()
                .stream()
                .filter(a -> isInDateRange(resolveAppointmentDate(a), window))
                .toList();

        long pendingApp = countAppointmentsByStatus(appointments, AppointmentStatus.PENDING);
        long approvedApp = countAppointmentsByStatus(appointments, AppointmentStatus.APPROVED);
        long rejectedApp = countAppointmentsByStatus(appointments, AppointmentStatus.REJECTED);
        long doneApp = countAppointmentsByStatus(appointments, AppointmentStatus.DONE);
        long notCompletedApp = countAppointmentsByStatus(appointments, AppointmentStatus.NOT_COMPLETED);

        List<ServiceJobDTO> jobs = filterJobsInRange(serviceJobService.getAllJobs(), window);
        long pendingJobs = countJobsByStatus(jobs, "PENDING");
        long inProgressJobs = countJobsByStatus(jobs, "IN_PROGRESS");
        long completedJobs = countJobsByStatus(jobs, "COMPLETED");
        long closedJobs = countJobsByStatus(jobs, "CLOSED");
        long cancelledJobs = countJobsByStatus(jobs, "CANCELLED");

        BigDecimal avgCompletionHours = calculateAverageCompletionHours(jobs);

        List<CustomerResponse> customers = customerService.getAllCustomers();
        long activeCustomers = customers.stream()
                .filter(c -> c.getStatus() == CustomerStatus.ACTIVE)
                .count();
        long newCustomers = customers.stream()
                .filter(c -> c.getCreatedAt() != null
                        && isInDateRange(c.getCreatedAt().toLocalDate(), window))
                .count();

        List<VehicleResponse> vehicles = vehicleService.getAllVehicles();
        long activeVehicles = vehicles.stream()
                .filter(v -> v.getStatus() == VehicleStatus.ACTIVE)
                .count();
        long newVehicles = vehicles.stream()
                .filter(v -> v.getCreatedAt() != null
                        && isInDateRange(v.getCreatedAt().toLocalDate(), window))
                .count();

        List<ServiceHistoryResponse> histories =
                fetchServiceHistoryInRange(window.startDate(), window.endDate());

        List<ServiceReminderResponse> reminders = serviceReminderService.getAllReminders(null);
        long pendingReminders = reminders.stream()
                .filter(r -> r.getStatus() == ServiceReminderStatus.PENDING).count();
        long sentReminders = reminders.stream()
                .filter(r -> r.getStatus() == ServiceReminderStatus.SENT).count();
        long cancelledReminders = reminders.stream()
                .filter(r -> r.getStatus() == ServiceReminderStatus.CANCELLED).count();
        long dueReminders = serviceReminderService.getDueReminders().size();

        List<NotificationResponse> notifications =
                fetchNotificationsInRange(window.startDate(), window.endDate());
        long sentNotifications = notifications.stream()
                .filter(n -> n.getStatus() == NotificationStatus.SENT).count();
        long failedNotifications = notifications.stream()
                .filter(n -> n.getStatus() == NotificationStatus.FAILED).count();
        long pendingNotifications = notifications.stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING).count();

        OperationalReportResponse response = new OperationalReportResponse();
        response.setMetadata(createMetadata(ReportType.OPERATIONAL, period, window));

        response.setTotalAppointments(appointments.size());
        response.setPendingAppointments(pendingApp);
        response.setApprovedAppointments(approvedApp);
        response.setRejectedAppointments(rejectedApp);
        response.setDoneAppointments(doneApp);
        response.setNotCompletedAppointments(notCompletedApp);
        response.setAppointmentCompletionRate(percentage(doneApp, appointments.size()));

        response.setTotalJobs(jobs.size());
        response.setPendingJobs(pendingJobs);
        response.setInProgressJobs(inProgressJobs);
        response.setCompletedJobs(completedJobs);
        response.setClosedJobs(closedJobs);
        response.setCancelledJobs(cancelledJobs);
        response.setJobCompletionRate(percentage(completedJobs + closedJobs, jobs.size()));
        response.setAverageJobCompletionHours(avgCompletionHours);

        response.setTotalCustomers(customers.size());
        response.setActiveCustomers(activeCustomers);
        response.setNewCustomersInPeriod(newCustomers);
        response.setTotalVehicles(vehicles.size());
        response.setActiveVehicles(activeVehicles);
        response.setNewVehiclesInPeriod(newVehicles);

        response.setCompletedServiceHistoriesInPeriod(histories.size());
        response.setTotalReminders(reminders.size());
        response.setPendingReminders(pendingReminders);
        response.setSentReminders(sentReminders);
        response.setCancelledReminders(cancelledReminders);
        response.setDueRemindersCount(dueReminders);
        response.setNotificationsInPeriod(notifications.size());
        response.setSentNotificationsInPeriod(sentNotifications);
        response.setFailedNotificationsInPeriod(failedNotifications);
        response.setPendingNotificationsInPeriod(pendingNotifications);

        response.setTopServicesPerformed(buildTopServices(jobs));
        response.setJobStatusDistribution(buildJobStatusSegments(jobs));
        response.setAppointmentStatusDistribution(buildAppointmentStatusSegments(appointments));

        return response;
    }

    // =====================================================
    // STOCK / INVENTORY REPORT BUILDER
    // =====================================================

    private StockReportResponse buildStockReport(
            ReportPeriod period,
            ReportDateRangeResolver.DateWindow window
    ) {
        List<StockItemResponse> allItems = stockItemService.getAllItems();
        List<StockCategoryResponse> categories = stockCategoryService.getAllCategories();
        List<StockItemResponse> lowStockItems = stockItemService.getLowStockItems();
        List<SupplierResponse> suppliers = supplierService.getAllSuppliers();

        long activeItems = allItems.stream().filter(StockItemResponse::isActive).count();
        long totalUnits = allItems.stream()
                .mapToLong(i -> i.getCurrentQuantity() != null ? i.getCurrentQuantity() : 0)
                .sum();

        BigDecimal totalCostVal = BigDecimal.ZERO;
        BigDecimal totalSellVal = BigDecimal.ZERO;

        Map<Long, List<StockItemResponse>> itemsByCategory = new LinkedHashMap<>();
        for (StockItemResponse item : allItems) {
            int qty = item.getCurrentQuantity() != null ? item.getCurrentQuantity() : 0;
            BigDecimal qtyBd = BigDecimal.valueOf(qty);
            BigDecimal unitCost = safeAmount(item.getUnitCost());
            BigDecimal sellPrice = safeAmount(item.getSellingPrice());

            totalCostVal = totalCostVal.add(unitCost.multiply(qtyBd));
            totalSellVal = totalSellVal.add(sellPrice.multiply(qtyBd));

            itemsByCategory
                    .computeIfAbsent(item.getCategoryId(), k -> new ArrayList<>())
                    .add(item);
        }

        List<StockReportResponse.CategoryStockSummary> categoryBreakdown = new ArrayList<>();
        for (Map.Entry<Long, List<StockItemResponse>> entry : itemsByCategory.entrySet()) {
            List<StockItemResponse> catItems = entry.getValue();
            String catName = catItems.get(0).getCategoryName() != null
                    ? catItems.get(0).getCategoryName()
                    : "Uncategorized";
            long catQty = 0;
            BigDecimal catCost = BigDecimal.ZERO;
            BigDecimal catSell = BigDecimal.ZERO;

            for (StockItemResponse item : catItems) {
                int qty = item.getCurrentQuantity() != null ? item.getCurrentQuantity() : 0;
                catQty += qty;
                catCost = catCost.add(safeAmount(item.getUnitCost()).multiply(BigDecimal.valueOf(qty)));
                catSell = catSell.add(safeAmount(item.getSellingPrice()).multiply(BigDecimal.valueOf(qty)));
            }

            categoryBreakdown.add(new StockReportResponse.CategoryStockSummary(
                    entry.getKey(),
                    catName,
                    catItems.size(),
                    catQty,
                    scale2(catCost),
                    scale2(catSell)
            ));
        }
        categoryBreakdown.sort(Comparator.comparing(StockReportResponse.CategoryStockSummary::getCostValuation).reversed());

        List<StockMovementResponse> movementsInPeriod = stockMovementService.getAllTransactions()
                .stream()
                .filter(m -> m.getCreatedAt() != null
                        && isInDateRange(m.getCreatedAt().toLocalDate(), window))
                .toList();

        Map<StockTransactionType, Long> movementQtyByType = new EnumMap<>(StockTransactionType.class);
        Map<StockTransactionType, Long> movementCountByType = new EnumMap<>(StockTransactionType.class);
        for (StockMovementResponse movement : movementsInPeriod) {
            if (movement.getTransactionType() != null) {
                long qty = movement.getQuantity() != null ? movement.getQuantity() : 0L;
                movementQtyByType.merge(movement.getTransactionType(), qty, Long::sum);
                movementCountByType.merge(movement.getTransactionType(), 1L, Long::sum);
            }
        }

        List<GRNResponse> grnsInPeriod = grnService.getAllGRNs()
                .stream()
                .filter(g -> isInDateRange(resolveGrnDate(g), window))
                .toList();

        long confirmedGrns = grnsInPeriod.stream().filter(g -> g.getStatus() == GRNStatus.CONFIRMED).count();
        long draftGrns = grnsInPeriod.stream().filter(g -> g.getStatus() == GRNStatus.DRAFT).count();
        long cancelledGrns = grnsInPeriod.stream().filter(g -> g.getStatus() == GRNStatus.CANCELLED).count();

        BigDecimal confirmedGrnSpend = grnsInPeriod.stream()
                .filter(g -> g.getStatus() == GRNStatus.CONFIRMED)
                .map(g -> safeAmount(g.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, StockReportResponse.SupplierSpendSummary> supplierSpendMap = new LinkedHashMap<>();
        for (GRNResponse grn : grnsInPeriod) {
            if (grn.getStatus() == GRNStatus.CONFIRMED && grn.getSupplierId() != null) {
                supplierSpendMap.compute(grn.getSupplierId(), (id, existing) -> {
                    if (existing == null) {
                        return new StockReportResponse.SupplierSpendSummary(
                                id,
                                grn.getSupplierName(),
                                1L,
                                scale2(safeAmount(grn.getTotalAmount()))
                        );
                    }
                    existing.setConfirmedGrnCount(existing.getConfirmedGrnCount() + 1);
                    existing.setTotalSpend(scale2(existing.getTotalSpend().add(safeAmount(grn.getTotalAmount()))));
                    return existing;
                });
            }
        }

        List<StockReportResponse.LowStockItemAlert> alerts = lowStockItems.stream()
                .map(item -> {
                    int current = item.getCurrentQuantity() != null ? item.getCurrentQuantity() : 0;
                    int reorder = item.getReorderLevel() != null ? item.getReorderLevel() : 0;
                    return new StockReportResponse.LowStockItemAlert(
                            item.getId(),
                            item.getItemCode(),
                            item.getItemName(),
                            item.getCategoryName(),
                            current,
                            reorder,
                            Math.max(0, reorder - current),
                            scale2(safeAmount(item.getUnitCost()))
                    );
                })
                .sorted(Comparator.comparingInt(StockReportResponse.LowStockItemAlert::getDeficit).reversed())
                .toList();

        List<ChartSeriesDTO.ChartSegment> movementSegments = new ArrayList<>();
        for (StockTransactionType type : StockTransactionType.values()) {
            long count = movementCountByType.getOrDefault(type, 0L);
            long qty = movementQtyByType.getOrDefault(type, 0L);
            movementSegments.add(new ChartSeriesDTO.ChartSegment(
                    type.name(),
                    count,
                    BigDecimal.valueOf(qty),
                    percentage(count, movementsInPeriod.size())
            ));
        }

        StockReportResponse response = new StockReportResponse();
        response.setMetadata(createMetadata(ReportType.STOCK, period, window));
        response.setTotalStockItems(allItems.size());
        response.setActiveStockItems(activeItems);
        response.setTotalCategories(categories.size());
        response.setTotalUnitsInStock(totalUnits);
        response.setLowStockCount(alerts.size());
        response.setTotalInventoryCostValue(scale2(totalCostVal));
        response.setTotalInventorySellingValue(scale2(totalSellVal));
        response.setPotentialGrossProfit(scale2(totalSellVal.subtract(totalCostVal)));

        response.setTotalMovementsInPeriod(movementsInPeriod.size());
        response.setStockInQuantity(movementQtyByType.getOrDefault(StockTransactionType.STOCK_IN, 0L));
        response.setStockOutQuantity(movementQtyByType.getOrDefault(StockTransactionType.STOCK_OUT, 0L));
        response.setDamageQuantity(movementQtyByType.getOrDefault(StockTransactionType.DAMAGE, 0L));
        response.setWastageQuantity(movementQtyByType.getOrDefault(StockTransactionType.WASTAGE, 0L));
        response.setAdjustmentInQuantity(movementQtyByType.getOrDefault(StockTransactionType.ADJUSTMENT_IN, 0L));
        response.setAdjustmentOutQuantity(movementQtyByType.getOrDefault(StockTransactionType.ADJUSTMENT_OUT, 0L));

        response.setTotalSuppliers(suppliers.size());
        response.setActiveSuppliers(suppliers.stream().filter(SupplierResponse::isActive).count());
        response.setGrnsInPeriod(grnsInPeriod.size());
        response.setConfirmedGrnsInPeriod(confirmedGrns);
        response.setDraftGrnsInPeriod(draftGrns);
        response.setCancelledGrnsInPeriod(cancelledGrns);
        response.setConfirmedGrnTotalSpend(scale2(confirmedGrnSpend));

        response.setLowStockItems(alerts);
        response.setCategoryBreakdown(categoryBreakdown);
        response.setSupplierSpendBreakdown(
                supplierSpendMap.values().stream()
                        .sorted(Comparator.comparing(StockReportResponse.SupplierSpendSummary::getTotalSpend).reversed())
                        .toList()
        );
        response.setMovementTypeDistribution(movementSegments);

        return response;
    }

    // =====================================================
    // FINANCIAL REPORT BUILDER
    // =====================================================

    private FinancialReportResponse buildFinancialReport(
            ReportPeriod period,
            ReportDateRangeResolver.DateWindow window
    ) {
        List<ServiceHistoryResponse> histories =
                fetchServiceHistoryInRange(window.startDate(), window.endDate());
        List<ServiceJobDTO> jobsInPeriod =
                filterJobsInRange(serviceJobService.getAllJobs(), window);
        List<AppointmentResponse> appointmentsInPeriod = appointmentService.getAllAppointments()
                .stream()
                .filter(a -> isInDateRange(resolveAppointmentDate(a), window))
                .toList();

        BigDecimal recordedRevenue = histories.stream()
                .map(h -> safeAmount(h.getTotalCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageTicket = histories.isEmpty()
                ? BigDecimal.ZERO
                : recordedRevenue.divide(BigDecimal.valueOf(histories.size()), 2, RoundingMode.HALF_UP);

        BigDecimal labourRevenue = BigDecimal.ZERO;
        BigDecimal partsRevenue = BigDecimal.ZERO;

        for (ServiceJobDTO job : jobsInPeriod) {
            if ("CANCELLED".equalsIgnoreCase(job.getStatus())) {
                continue;
            }
            for (ServiceJobItemDTO labour : serviceJobItemService.getItemsByJobId(job.getId())) {
                labourRevenue = labourRevenue.add(resolveLabourLineAmount(labour));
            }
            for (ServiceJobPartDTO part : serviceJobPartService.getPartsByJobId(job.getId())) {
                partsRevenue = partsRevenue.add(resolvePartLineAmount(part));
            }
        }

        List<GRNResponse> grnsInPeriod = grnService.getAllGRNs()
                .stream()
                .filter(g -> isInDateRange(resolveGrnDate(g), window))
                .toList();

        BigDecimal confirmedGrnSpend = grnsInPeriod.stream()
                .filter(g -> g.getStatus() == GRNStatus.CONFIRMED)
                .map(g -> safeAmount(g.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal draftGrnValue = grnsInPeriod.stream()
                .filter(g -> g.getStatus() == GRNStatus.DRAFT)
                .map(g -> safeAmount(g.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCreditPool = customerService.getAllCustomers()
                .stream()
                .filter(c -> c.getStatus() == CustomerStatus.ACTIVE)
                .map(c -> safeAmount(c.getCreditLimit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<EmployeeResponse> activeEmployees = employeeService.getAllEmployees()
                .stream()
                .filter(EmployeeResponse::active)
                .toList();

        BigDecimal activeStaffDailyRate = activeEmployees.stream()
                .map(e -> safeAmount(e.paymentRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        
                BigDecimal billableTotal = labourRevenue.add(partsRevenue);

                // Estimated operating difference, not actual cash flow.
                BigDecimal estimatedRevenue = recordedRevenue;

                BigDecimal netCashEstimate =
                        estimatedRevenue.subtract(confirmedGrnSpend);

        List<ChartSeriesDTO.TimeSeriesPoint> trend = buildTimeSeriesTrend(
                period,
                window,
                jobsInPeriod,
                appointmentsInPeriod,
                histories,
                grnsInPeriod
        );

        List<ChartSeriesDTO.ChartSegment> composition = List.of(
                new ChartSeriesDTO.ChartSegment(
                        "LABOUR_BILLABLE",
                        jobsInPeriod.size(),
                        scale2(labourRevenue),
                        percentage(labourRevenue, billableTotal)
                ),
                new ChartSeriesDTO.ChartSegment(
                        "PARTS_BILLABLE",
                        jobsInPeriod.size(),
                        scale2(partsRevenue),
                        percentage(partsRevenue, billableTotal)
                )
        );

        FinancialReportResponse response = new FinancialReportResponse();
        response.setMetadata(createMetadata(ReportType.FINANCIAL, period, window));
        response.setRecordedServiceRevenue(scale2(recordedRevenue));
        response.setRecordedServiceCount(histories.size());
        response.setAverageServiceTicketValue(scale2(averageTicket));
        response.setJobLabourRevenue(scale2(labourRevenue));
        response.setJobPartsRevenue(scale2(partsRevenue));
        response.setJobBillableTotal(scale2(billableTotal));
        response.setConfirmedGrnProcurementSpend(scale2(confirmedGrnSpend));
        response.setDraftGrnPendingValue(scale2(draftGrnValue));
        response.setNetOperatingCashEstimate(scale2(netCashEstimate));
        response.setTotalCustomerCreditLimitPool(scale2(totalCreditPool));
        response.setActiveEmployeeCount(activeEmployees.size());
        response.setActiveStaffDailyRateTotal(scale2(activeStaffDailyRate));
        
        response.setBillingIntegrationNote(
        "Estimated operating difference = recorded service revenue "
        + "minus confirmed GRN procurement value. "
        + "This is not actual cash flow or net profit. "
        + "Job billable totals are shown separately and are not "
        + "added to recorded service revenue to avoid double counting. "
        + "Actual cash reporting requires payment and cash ledger data."
        );

        response.setFinancialTrend(trend);
        response.setRevenueComposition(composition);

        return response;
    }

    // =====================================================
    // USER & TECHNICIAN PERFORMANCE ANALYTICS BUILDER
    // =====================================================

    private PerformanceReportResponse buildPerformanceReport(
            ReportPeriod period,
            ReportDateRangeResolver.DateWindow window,
            Long technicianIdFilter,
            Long actorUserIdFilter
    ) {
        List<ServiceJobDTO> jobsInPeriod =
                filterJobsInRange(serviceJobService.getAllJobs(), window);
        List<ServiceHistoryResponse> historiesInPeriod =
                fetchServiceHistoryInRange(window.startDate(), window.endDate());
        List<EmployeeResponse> employees = employeeService.getAllEmployees();

        LocalDateTime startDateTime = window.startDate().atStartOfDay();
        LocalDateTime endDateTime = window.endDate().atTime(LocalTime.MAX);
        List<AuditLogResponse> auditLogs =
                auditLogService.getAuditLogsByDateRange(startDateTime, endDateTime);

        Map<Long, PerformanceReportResponse.TechnicianPerformanceItem> techMap = new LinkedHashMap<>();

        for (ServiceJobDTO job : jobsInPeriod) {
            Long techId = job.getAssignedEmployeeId();
            if (techId == null) {
                continue;
            }
            if (technicianIdFilter != null && !technicianIdFilter.equals(techId)) {
                continue;
            }

            PerformanceReportResponse.TechnicianPerformanceItem item = techMap.computeIfAbsent(
                    techId,
                    id -> {
                        PerformanceReportResponse.TechnicianPerformanceItem t =
                                new PerformanceReportResponse.TechnicianPerformanceItem();
                        t.setTechnicianId(id);
                        t.setTotalLabourHours(BigDecimal.ZERO);
                        t.setTotalBillableValue(BigDecimal.ZERO);
                        t.setServiceHistoryRevenue(BigDecimal.ZERO);
                        t.setAverageCompletionHours(BigDecimal.ZERO);
                        t.setCompletionRate(BigDecimal.ZERO);
                        return t;
                    }
            );

            item.setTotalJobsAssigned(item.getTotalJobsAssigned() + 1);
            String status = job.getStatus() != null ? job.getStatus().toUpperCase() : "";
            switch (status) {
                case "COMPLETED", "CLOSED" -> item.setCompletedJobs(item.getCompletedJobs() + 1);
                case "IN_PROGRESS" -> item.setInProgressJobs(item.getInProgressJobs() + 1);
                case "CANCELLED" -> item.setCancelledJobs(item.getCancelledJobs() + 1);
                default -> { }
            }

            if (!"CANCELLED".equals(status)) {
                for (ServiceJobItemDTO labour : serviceJobItemService.getItemsByJobId(job.getId())) {
                    BigDecimal hours = labour.getLabourHours() != null
                            ? BigDecimal.valueOf(labour.getLabourHours())
                            : BigDecimal.ZERO;
                    item.setTotalLabourHours(item.getTotalLabourHours().add(hours));
                    item.setTotalBillableValue(item.getTotalBillableValue().add(resolveLabourLineAmount(labour)));
                }
                for (ServiceJobPartDTO part : serviceJobPartService.getPartsByJobId(job.getId())) {
                    item.setTotalBillableValue(item.getTotalBillableValue().add(resolvePartLineAmount(part)));
                }
            }
        }

        // Accumulate completion durations per technician
        // Accumulate completion durations per technician
Map<Long, List<Double>> completionHoursByTech = new HashMap<>();

for (ServiceJobDTO job : jobsInPeriod) {

    Long techId = job.getAssignedEmployeeId();
    String status = job.getStatus();

    if (techId == null) {
        continue;
    }

    if (technicianIdFilter != null
            && !technicianIdFilter.equals(techId)) {
        continue;
    }

    boolean completed = "COMPLETED".equalsIgnoreCase(status)
            || "CLOSED".equalsIgnoreCase(status);

    if (!completed
            || job.getJobDate() == null
            || job.getActualCompletionDate() == null
            || job.getActualCompletionDate()
                    .isBefore(job.getJobDate())) {
        continue;
    }

    double hours = Duration.between(
            job.getJobDate(),
            job.getActualCompletionDate()
    ).toMinutes() / 60.0;

    completionHoursByTech
            .computeIfAbsent(techId, k -> new ArrayList<>())
            .add(hours);
}

        for (ServiceHistoryResponse history : historiesInPeriod) {
            Long techId = history.getTechnicianId();
            if (techId == null) {
                continue;
            }
            if (technicianIdFilter != null && !technicianIdFilter.equals(techId)) {
                continue;
            }

            PerformanceReportResponse.TechnicianPerformanceItem item = techMap.computeIfAbsent(
                    techId,
                    id -> {
                        PerformanceReportResponse.TechnicianPerformanceItem t =
                                new PerformanceReportResponse.TechnicianPerformanceItem();
                        t.setTechnicianId(id);
                        t.setTotalLabourHours(BigDecimal.ZERO);
                        t.setTotalBillableValue(BigDecimal.ZERO);
                        t.setServiceHistoryRevenue(BigDecimal.ZERO);
                        t.setAverageCompletionHours(BigDecimal.ZERO);
                        t.setCompletionRate(BigDecimal.ZERO);
                        return t;
                    }
            );
            item.setCompletedHistoryCount(item.getCompletedHistoryCount() + 1);
            item.setServiceHistoryRevenue(item.getServiceHistoryRevenue().add(safeAmount(history.getTotalCost())));
        }

        for (PerformanceReportResponse.TechnicianPerformanceItem item : techMap.values()) {
            item.setCompletionRate(percentage(item.getCompletedJobs(), item.getTotalJobsAssigned()));
            item.setTotalLabourHours(scale2(item.getTotalLabourHours()));
            item.setTotalBillableValue(scale2(item.getTotalBillableValue()));
            item.setServiceHistoryRevenue(scale2(item.getServiceHistoryRevenue()));

            List<Double> hoursList = completionHoursByTech.getOrDefault(item.getTechnicianId(), List.of());
            if (!hoursList.isEmpty()) {
                double avg = hoursList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                item.setAverageCompletionHours(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            }
        }

        List<PerformanceReportResponse.TechnicianPerformanceItem> technicianPerformances = techMap.values()
                .stream()
                .sorted(Comparator
                        .comparing(PerformanceReportResponse.TechnicianPerformanceItem::getCompletedJobs, Comparator.reverseOrder())
                        .thenComparing(PerformanceReportResponse.TechnicianPerformanceItem::getServiceHistoryRevenue, Comparator.reverseOrder()))
                .toList();

        // System user audit log performance
        // System user audit log performance
        Map<Long, List<AuditLogResponse>> logsByActor =
        auditLogs.stream()
                .filter(Objects::nonNull)
                .filter(log -> log.getActorUserId() != null)
                .filter(log -> actorUserIdFilter == null
                        || actorUserIdFilter.equals(
                                log.getActorUserId()))
                .collect(Collectors.groupingBy(
                        AuditLogResponse::getActorUserId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PerformanceReportResponse.UserAuditActivityItem> userActivities = new ArrayList<>();
        for (Map.Entry<Long, List<AuditLogResponse>> entry : logsByActor.entrySet()) {
            List<AuditLogResponse> actorLogs = entry.getValue();
            Map<String, Long> byAction = actorLogs.stream()
        .collect(Collectors.groupingBy(
                log -> log.getAction() != null
                        ? log.getAction() : "UNKNOWN",
                TreeMap::new,
                Collectors.counting()
        ));

        Map<String, Long> byEntity = actorLogs.stream()
        .collect(Collectors.groupingBy(
                log -> log.getEntityType() != null
                        ? log.getEntityType() : "UNKNOWN",
                TreeMap::new,
                Collectors.counting()
        ));     
            LocalDateTime lastActivity = actorLogs.stream()
                    .map(AuditLogResponse::getTimestamp)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            PerformanceReportResponse.UserAuditActivityItem item = new PerformanceReportResponse.UserAuditActivityItem();
            item.setActorUserId(entry.getKey());
            item.setTotalActions(actorLogs.size());
            item.setActionsByType(byAction);
            item.setActionsByEntity(byEntity);
            item.setLastActivityAt(lastActivity);
            userActivities.add(item);
        }
        userActivities.sort(Comparator.comparingLong(PerformanceReportResponse.UserAuditActivityItem::getTotalActions).reversed());

        List<PerformanceReportResponse.EmployeeRosterSummary> roster = employees.stream()
                .map(e -> new PerformanceReportResponse.EmployeeRosterSummary(
                        e.id() != null ? e.id().toString() : null,
                        e.employeeCode(),
                        e.firstName() + " " + e.lastName(),
                        e.position() != null ? e.position().name() : "UNKNOWN",
                        scale2(safeAmount(e.paymentRate())),
                        e.active()
                ))
                .toList();

        List<ChartSeriesDTO.ChartSegment> positionSegments = new ArrayList<>();
        for (EmployeePosition position : EmployeePosition.values()) {
            long count = employees.stream().filter(e -> e.position() == position).count();
            positionSegments.add(new ChartSeriesDTO.ChartSegment(
                    position.name(),
                    count,
                    BigDecimal.valueOf(count),
                    percentage(count, employees.size())
            ));
        }

        PerformanceReportResponse response = new PerformanceReportResponse();
        response.setMetadata(createMetadata(ReportType.PERFORMANCE, period, window));
        response.setTotalEmployees(employees.size());
        response.setActiveEmployees(employees.stream().filter(EmployeeResponse::active).count());
        response.setActiveTechniciansTracked(technicianPerformances.size());
        response.setSystemActorsTracked(userActivities.size());
        response.setTotalAuditEventsInPeriod(
                userActivities.stream().mapToLong(PerformanceReportResponse.UserAuditActivityItem::getTotalActions).sum()
        );
        response.setTechnicianPerformances(technicianPerformances);
        response.setUserAuditActivities(userActivities);
        response.setEmployeeRoster(roster);
        response.setEmployeePositionDistribution(positionSegments);

        return response;
    }

    // =====================================================
    // SHARED HELPERS & DATA COLLECTORS
    // =====================================================

    private List<ServiceHistoryResponse> fetchServiceHistoryInRange(LocalDate start, LocalDate end) {
        List<ServiceHistoryResponse> all = new ArrayList<>();
        int page = 0;
        Page<ServiceHistoryResponse> result;
        do {
            result = serviceHistoryService.getServiceHistoryByDateRange(start, end, page, PAGE_FETCH_SIZE);
            all.addAll(result.getContent());
            page++;
        } while (result.hasNext());
        return all;
    }

    private List<NotificationResponse> fetchNotificationsInRange(LocalDate start, LocalDate end) {
        List<NotificationResponse> all = new ArrayList<>();
        int page = 0;
        Page<NotificationResponse> result;
        do {
            result = notificationService.getAllNotifications(null, null, page, PAGE_FETCH_SIZE);
            for (NotificationResponse n : result.getContent()) {
                if (n.getCreatedAt() != null && isInDateRange(n.getCreatedAt().toLocalDate(), new ReportDateRangeResolver.DateWindow(start, end))) {
                    all.add(n);
                }
            }
            page++;
        } while (result.hasNext());
        return all;
    }

    private List<ServiceJobDTO> filterJobsInRange(
            List<ServiceJobDTO> allJobs,
            ReportDateRangeResolver.DateWindow window
    ) {
        return allJobs.stream()
                .filter(j -> j.getJobDate() != null && isInDateRange(j.getJobDate().toLocalDate(), window))
                .toList();
    }

    private List<OperationalReportResponse.TopServiceSummary> buildTopServices(List<ServiceJobDTO> jobs) {
        Map<Long, String> serviceNames = serviceItemService.getAllServiceItems()
                .stream()
                .collect(Collectors.toMap(ServiceItemDTO::getId, ServiceItemDTO::getName, (a, b) -> a));

        Map<Long, OperationalReportResponse.TopServiceSummary> summaryMap = new LinkedHashMap<>();

        for (ServiceJobDTO job : jobs) {
            if ("CANCELLED".equalsIgnoreCase(job.getStatus())) {
                continue;
            }
            for (ServiceJobItemDTO item : serviceJobItemService.getItemsByJobId(job.getId())) {
                Long serviceItemId = item.getServiceItemId();
                if (serviceItemId == null) {
                    continue;
                }
                BigDecimal hours = item.getLabourHours() != null
                        ? BigDecimal.valueOf(item.getLabourHours())
                        : BigDecimal.ZERO;
                BigDecimal amount = resolveLabourLineAmount(item);

                summaryMap.compute(serviceItemId, (id, existing) -> {
                    if (existing == null) {
                        return new OperationalReportResponse.TopServiceSummary(
                                id,
                                serviceNames.getOrDefault(id, "Service #" + id),
                                1L,
                                scale2(hours),
                                scale2(amount)
                        );
                    }
                    existing.setTimesPerformed(existing.getTimesPerformed() + 1);
                    existing.setTotalLabourHours(scale2(existing.getTotalLabourHours().add(hours)));
                    existing.setTotalLabourAmount(scale2(existing.getTotalLabourAmount().add(amount)));
                    return existing;
                });
            }
        }

        return summaryMap.values().stream()
                .sorted(Comparator.comparingLong(OperationalReportResponse.TopServiceSummary::getTimesPerformed).reversed())
                .limit(10)
                .toList();
    }

    private List<ChartSeriesDTO.TimeSeriesPoint> buildTimeSeriesTrend(
            ReportPeriod period,
            ReportDateRangeResolver.DateWindow window,
            List<ServiceJobDTO> jobs,
            List<AppointmentResponse> appointments,
            List<ServiceHistoryResponse> histories,
            List<GRNResponse> grns
    ) {
        boolean monthlyBuckets = period == ReportPeriod.YEARLY
                || java.time.temporal.ChronoUnit.DAYS.between(window.startDate(), window.endDate()) > 62;

        Map<String, ChartSeriesDTO.TimeSeriesPoint> buckets = new LinkedHashMap<>();
        if (monthlyBuckets) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
            YearMonth current = YearMonth.from(window.startDate());
            YearMonth endMonth = YearMonth.from(window.endDate());
            while (!current.isAfter(endMonth)) {
                String key = current.format(fmt);
                buckets.put(key, new ChartSeriesDTO.TimeSeriesPoint(key, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO));
                current = current.plusMonths(1);
            }
        } else {
            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
            LocalDate current = window.startDate();
            while (!current.isAfter(window.endDate())) {
                String key = current.format(fmt);
                buckets.put(key, new ChartSeriesDTO.TimeSeriesPoint(key, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO));
                current = current.plusDays(1);
            }
        }

        DateTimeFormatter bucketFormatter = monthlyBuckets
                ? DateTimeFormatter.ofPattern("yyyy-MM")
                : DateTimeFormatter.ISO_LOCAL_DATE;

        for (ServiceJobDTO job : jobs) {
            if (job.getJobDate() != null) {
                String key = job.getJobDate().toLocalDate().format(bucketFormatter);
                ChartSeriesDTO.TimeSeriesPoint point = buckets.get(key);
                if (point != null) {
                    point.setJobsCount(point.getJobsCount() + 1);
                }
            }
        }

        for (AppointmentResponse app : appointments) {
            LocalDate d = resolveAppointmentDate(app);
            if (d != null) {
                String key = d.format(bucketFormatter);
                ChartSeriesDTO.TimeSeriesPoint point = buckets.get(key);
                if (point != null) {
                    point.setAppointmentsCount(point.getAppointmentsCount() + 1);
                }
            }
        }

        for (ServiceHistoryResponse history : histories) {
            if (history.getServiceDate() != null) {
                String key = history.getServiceDate().format(bucketFormatter);
                ChartSeriesDTO.TimeSeriesPoint point = buckets.get(key);
                if (point != null) {
                    point.setRevenue(scale2(point.getRevenue().add(safeAmount(history.getTotalCost()))));
                }
            }
        }

        for (GRNResponse grn : grns) {
            if (grn.getStatus() == GRNStatus.CONFIRMED) {
                LocalDate d = resolveGrnDate(grn);
                if (d != null) {
                    String key = d.format(bucketFormatter);
                    ChartSeriesDTO.TimeSeriesPoint point = buckets.get(key);
                    if (point != null) {
                        point.setProcurementSpend(
                                scale2(point.getProcurementSpend().add(safeAmount(grn.getTotalAmount())))
                        );
                    }
                }
            }
        }

        return new ArrayList<>(buckets.values());
    }

    private List<ChartSeriesDTO.ChartSegment> buildJobStatusSegments(List<ServiceJobDTO> jobs) {
        List<String> statuses = List.of("PENDING", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED");
        List<ChartSeriesDTO.ChartSegment> segments = new ArrayList<>();
        for (String status : statuses) {
            long count = countJobsByStatus(jobs, status);
            segments.add(new ChartSeriesDTO.ChartSegment(
                    status,
                    count,
                    BigDecimal.valueOf(count),
                    percentage(count, jobs.size())
            ));
        }
        return segments;
    }

    private List<ChartSeriesDTO.ChartSegment> buildAppointmentStatusSegments(List<AppointmentResponse> appointments) {
        List<ChartSeriesDTO.ChartSegment> segments = new ArrayList<>();
        for (AppointmentStatus status : AppointmentStatus.values()) {
            long count = countAppointmentsByStatus(appointments, status);
            segments.add(new ChartSeriesDTO.ChartSegment(
                    status.name(),
                    count,
                    BigDecimal.valueOf(count),
                    percentage(count, appointments.size())
            ));
        }
        return segments;
    }

    private List<ChartSeriesDTO.ChartSegment> buildCategoryValuationSegments(
            List<StockReportResponse.CategoryStockSummary> categories
    ) {
        BigDecimal total = categories.stream()
                .map(StockReportResponse.CategoryStockSummary::getCostValuation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return categories.stream()
                .map(c -> new ChartSeriesDTO.ChartSegment(
                        c.getCategoryName(),
                        c.getTotalQuantity(),
                        c.getCostValuation(),
                        percentage(c.getCostValuation(), total)
                ))
                .toList();
    }

    private List<ChartSeriesDTO.ChartSegment> buildNotificationSegments(List<NotificationResponse> notifications) {
        List<ChartSeriesDTO.ChartSegment> segments = new ArrayList<>();
        for (NotificationStatus status : NotificationStatus.values()) {
            long count = notifications.stream().filter(n -> n.getStatus() == status).count();
            segments.add(new ChartSeriesDTO.ChartSegment(
                    status.name(),
                    count,
                    BigDecimal.valueOf(count),
                    percentage(count, notifications.size())
            ));
        }
        return segments;
    }

    private BigDecimal resolveLabourLineAmount(ServiceJobItemDTO item) {
        if (item.getTotalAmount() != null) {
            return item.getTotalAmount();
        }
        if (item.getLabourHours() != null
                && item.getLabourHours() > 0
                && item.getLabourRate() != null
                && item.getLabourRate().signum() >= 0) {
            return invoiceCalculationService.calculateLineAmount(
                    BigDecimal.valueOf(item.getLabourHours()),
                    item.getLabourRate()
            );
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolvePartLineAmount(ServiceJobPartDTO part) {
        if (part.getTotalAmount() != null) {
            return part.getTotalAmount();
        }
        if (part.getQuantity() != null
                && part.getQuantity() > 0
                && part.getUnitPrice() != null
                && part.getUnitPrice().signum() >= 0) {
            return invoiceCalculationService.calculateLineAmount(
                    BigDecimal.valueOf(part.getQuantity()),
                    part.getUnitPrice()
            );
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageCompletionHours(
        List<ServiceJobDTO> jobs) {

    List<Double> durations = new ArrayList<>();

    for (ServiceJobDTO job : jobs) {

        String status = job.getStatus();

        boolean completed =
                "COMPLETED".equalsIgnoreCase(status)
                || "CLOSED".equalsIgnoreCase(status);

        if (!completed
                || job.getJobDate() == null
                || job.getActualCompletionDate() == null
                || job.getActualCompletionDate()
                        .isBefore(job.getJobDate())) {
            continue;
        }

        double hours = Duration.between(
                job.getJobDate(),
                job.getActualCompletionDate()
        ).toMinutes() / 60.0;

        durations.add(hours);
    }

    if (durations.isEmpty()) {
        return BigDecimal.ZERO.setScale(
                2, RoundingMode.HALF_UP);
    }

    double avg = durations.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

    return BigDecimal.valueOf(avg)
            .setScale(2, RoundingMode.HALF_UP);
}

    private LocalDate resolveAppointmentDate(AppointmentResponse appointment) {
        if (appointment.getConfirmedDate() != null) {
            return appointment.getConfirmedDate();
        }
        if (appointment.getRequestedDate() != null) {
            return appointment.getRequestedDate();
        }
        return appointment.getCreatedAt() != null ? appointment.getCreatedAt().toLocalDate() : null;
    }

    private LocalDate resolveGrnDate(GRNResponse grn) {
        if (grn.getReceivedAt() != null) {
            return grn.getReceivedAt().toLocalDate();
        }
        return grn.getCreatedAt() != null ? grn.getCreatedAt().toLocalDate() : null;
    }

    private boolean isInDateRange(LocalDate date, ReportDateRangeResolver.DateWindow window) {
        return date != null
                && !date.isBefore(window.startDate())
                && !date.isAfter(window.endDate());
    }

    private long countAppointmentsByStatus(List<AppointmentResponse> appointments, AppointmentStatus status) {
        return appointments.stream().filter(a -> a.getStatus() == status).count();
    }

    private long countJobsByStatus(List<ServiceJobDTO> jobs, String status) {
        return jobs.stream()
                .filter(j -> j.getStatus() != null && j.getStatus().equalsIgnoreCase(status))
                .count();
    }

    private ReportMetadata createMetadata(
            ReportType type,
            ReportPeriod period,
            ReportDateRangeResolver.DateWindow window
    ) {
        return new ReportMetadata(
                type,
                period,
                window.startDate(),
                window.endDate(),
                LocalDateTime.now(ReportDateRangeResolver.BUSINESS_ZONE),
                ReportDateRangeResolver.BUSINESS_ZONE.getId(),
                CURRENCY
        );
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale2(BigDecimal value) {
        return safeAmount(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0 || part == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }
}