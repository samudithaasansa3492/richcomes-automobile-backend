package com.richcomes.garage.billing.service;

import com.richcomes.garage.billing.dto.JobBillingDetails;
import com.richcomes.garage.billing.enums.InvoiceItemType;
import com.richcomes.garage.inventory.stockitem.dto.responce.StockItemResponse;
import com.richcomes.garage.inventory.stockitem.service.StockItemService;
import com.richcomes.garage.service.dto.ServiceItemDTO;
import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.dto.ServiceJobItemDTO;
import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import com.richcomes.garage.service.service.ServiceItemService;
import com.richcomes.garage.service.service.ServiceJobItemService;
import com.richcomes.garage.service.service.ServiceJobPartService;
import com.richcomes.garage.service.service.ServiceJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobBillingAdapterTest {

    private ServiceJobService jobService;
    private ServiceJobItemService jobItemService;
    private ServiceJobPartService jobPartService;
    private ServiceItemService serviceItemService;
    private StockItemService stockItemService;
    private InvoiceCalculationService calculationService;
    private JobBillingAdapter adapter;

    @BeforeEach
    void setUp() {
        jobService = mock(ServiceJobService.class);
        jobItemService = mock(ServiceJobItemService.class);
        jobPartService = mock(ServiceJobPartService.class);
        serviceItemService = mock(ServiceItemService.class);
        stockItemService = mock(StockItemService.class);
        calculationService = new InvoiceCalculationService();

        adapter = new JobBillingAdapter(
                jobService,
                jobItemService,
                jobPartService,
                serviceItemService,
                stockItemService,
                calculationService
        );
    }

    @Test
    void combinesLabourAndPartsUsingJobPrices() {
        stubJob();

        ServiceJobItemDTO labour = labourLine(1L, 1.5);
        labour.setTotalAmount(new BigDecimal("99999"));

        ServiceJobPartDTO part = new ServiceJobPartDTO();
        part.setServiceJobId(1L);
        part.setPartId(20L);
        part.setQuantity(2.0);
        part.setUnitPrice(new BigDecimal("500.00"));
        part.setTotalAmount(new BigDecimal("99999"));

        ServiceItemDTO service = new ServiceItemDTO();
        service.setName("Oil-change labour");
        service.setDefaultLabourRate(new BigDecimal("9000.00"));

        StockItemResponse stock = new StockItemResponse();
        stock.setItemName("Oil filter");
        stock.setSellingPrice(new BigDecimal("8000.00"));

        when(jobItemService.getItemsByJobId(1L))
                .thenReturn(List.of(labour));
        when(jobPartService.getPartsByJobId(1L))
                .thenReturn(List.of(part));
        when(serviceItemService.getServiceItemById(10L))
                .thenReturn(service);
        when(stockItemService.getItemById(20L))
                .thenReturn(stock);

        JobBillingDetails result = adapter.getBillingDetails(1L);

        assertEquals(1L, result.getServiceJobId());
        assertEquals(2L, result.getCustomerId());
        assertEquals(3L, result.getVehicleId());
        assertEquals("JOB-0001", result.getJobNo());
        assertEquals("COMPLETED", result.getJobStatus());
        assertEquals(2, result.getItems().size());

        var labourResult = result.getItems().get(0);
        assertEquals(InvoiceItemType.LABOUR, labourResult.getItemType());
        assertEquals(10L, labourResult.getItemReferenceId());
        assertEquals("Oil-change labour", labourResult.getDescription());
        assertAmount("1.5", labourResult.getQuantity());
        assertAmount("1000.00", labourResult.getUnitPrice());

        var partResult = result.getItems().get(1);
        assertEquals(InvoiceItemType.PART, partResult.getItemType());
        assertEquals(20L, partResult.getItemReferenceId());
        assertEquals("Oil filter", partResult.getDescription());
        assertAmount("2", partResult.getQuantity());
        assertAmount("500.00", partResult.getUnitPrice());

        assertAmount(
                "2500.00",
                calculationService.calculateSubtotal(result.getItems())
        );
    }

    @Test
    void rejectsLineFromAnotherJob() {
        stubJob();

        when(jobItemService.getItemsByJobId(1L))
                .thenReturn(List.of(labourLine(99L, 1.0)));

        assertThrows(
                IllegalStateException.class,
                () -> adapter.getBillingDetails(1L)
        );

        verifyNoInteractions(serviceItemService, stockItemService);
    }

    @Test
    void rejectsInvalidQuantities() {
        stubJob();

        ServiceItemDTO service = new ServiceItemDTO();
        service.setName("Labour");

        when(serviceItemService.getServiceItemById(10L))
                .thenReturn(service);

        Double[] invalidQuantities = {
                null,
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (Double quantity : invalidQuantities) {
            when(jobItemService.getItemsByJobId(1L))
                    .thenReturn(List.of(labourLine(1L, quantity)));

            assertThrows(
                    IllegalStateException.class,
                    () -> adapter.getBillingDetails(1L),
                    "Expected rejection for quantity: " + quantity
            );
        }
    }

    @Test
    void rejectsJobWithoutBillableLines() {
        stubJob();

        when(jobItemService.getItemsByJobId(1L))
                .thenReturn(List.of());
        when(jobPartService.getPartsByJobId(1L))
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> adapter.getBillingDetails(1L)
        );
    }

    @Test
    void rejectsInvalidJobIdBeforeCallingServices() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.getBillingDetails(0L)
        );

        verifyNoInteractions(
                jobService,
                jobItemService,
                jobPartService,
                serviceItemService,
                stockItemService
        );
    }

    private void stubJob() {
        ServiceJobDTO job = new ServiceJobDTO();
        job.setId(1L);
        job.setJobNo("JOB-0001");
        job.setCustomerId(2L);
        job.setVehicleId(3L);
        job.setStatus("COMPLETED");

        when(jobService.getJobById(1L)).thenReturn(job);
    }

    private ServiceJobItemDTO labourLine(
            Long jobId,
            Double hours
    ) {
        ServiceJobItemDTO line = new ServiceJobItemDTO();
        line.setServiceJobId(jobId);
        line.setServiceItemId(10L);
        line.setLabourHours(hours);
        line.setLabourRate(new BigDecimal("1000.00"));
        return line;
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {
        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }
}