package com.richcomes.garage.billing.service;

import com.richcomes.garage.billing.dto.InvoiceResponse;
import com.richcomes.garage.billing.entity.Invoice;
import com.richcomes.garage.billing.enums.InvoiceStatus;
import com.richcomes.garage.billing.repository.InvoiceItemRepository;
import com.richcomes.garage.billing.repository.InvoiceRepository;
import com.richcomes.garage.billing.dto.InvoiceItemRequest;
import com.richcomes.garage.billing.dto.JobBillingDetails;
import com.richcomes.garage.billing.enums.InvoiceItemType;
import com.richcomes.garage.billing.entity.InvoiceItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceServiceTest {

    @Test
    void returnsExistingInvoiceForRepeatedJobRequest() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceCalculationService calculations =
                new InvoiceCalculationService();
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                calculations,
                invoices,
                invoiceItems
        );

        Invoice existing = mock(Invoice.class);

        when(existing.getId()).thenReturn(50L);
        when(existing.getServiceJobId()).thenReturn(10);
        when(existing.getCustomerId()).thenReturn(2L);
        when(existing.getVehicleId()).thenReturn(3L);
        when(existing.getStatus()).thenReturn(InvoiceStatus.DRAFT);
        when(existing.getInvoiceDate())
                .thenReturn(LocalDateTime.of(2026, 11, 21, 10, 0));
        when(existing.getSubtotal())
                .thenReturn(new BigDecimal("2500.00"));
        when(existing.getDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(existing.getTotalAmount())
                .thenReturn(new BigDecimal("2500.00"));

        when(invoices.findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                10,
                InvoiceStatus.CANCELLED
        )).thenReturn(Optional.of(existing));

        when(invoiceItems.findByInvoice_IdOrderByIdAsc(50L))
                .thenReturn(List.of());

        InvoiceResponse response = service.createDraft(10L);

        assertEquals(50L, response.getId());
        assertEquals(10L, response.getServiceJobId());
        assertEquals(InvoiceStatus.DRAFT, response.getStatus());

        verify(invoices, never()).save(any(Invoice.class));
        verify(invoiceItems, never()).saveAll(anyList());
        verifyNoInteractions(adapter);
    }

    @Test
    void createsDraftWithJobPricesAndCalculatedAmounts() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                new InvoiceCalculationService(),
                invoices,
                invoiceItems
        );

        InvoiceItemRequest line = new InvoiceItemRequest();
        line.setItemType(InvoiceItemType.LABOUR);
        line.setItemReferenceId(20L);
        line.setDescription("Repair labour");
        line.setQuantity(new BigDecimal("1.5"));
        line.setUnitPrice(new BigDecimal("1000.00"));

        JobBillingDetails job = new JobBillingDetails();
        job.setServiceJobId(10L);
        job.setCustomerId(2L);
        job.setVehicleId(3L);
        job.setJobStatus("COMPLETED");
        job.setItems(List.of(line));

        when(invoices.findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                10,
                InvoiceStatus.CANCELLED
        )).thenReturn(Optional.empty());

        when(adapter.getBillingDetails(10L)).thenReturn(job);

        when(invoices.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(invoiceItems.saveAll(
                org.mockito.ArgumentMatchers.<InvoiceItem>anyList()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceResponse response = service.createDraft(10L);

        assertEquals(InvoiceStatus.DRAFT, response.getStatus());
        assertEquals(2L, response.getCustomerId());
        assertEquals(3L, response.getVehicleId());
        assertEquals(1, response.getItems().size());

        assertEquals(
                0,
                new BigDecimal("1500.00")
                        .compareTo(response.getSubtotal())
        );

        assertEquals(
                0,
                new BigDecimal("1500.00")
                        .compareTo(response.getItems().get(0).getTotalAmount())
        );

        verify(invoices).save(any(Invoice.class));
        verify(invoiceItems).saveAll(anyList());
    }

    @Test
    void rejectsDraftCreationForPendingJob() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                new InvoiceCalculationService(),
                invoices,
                invoiceItems
        );

        JobBillingDetails job = new JobBillingDetails();
        job.setServiceJobId(10L);
        job.setCustomerId(2L);
        job.setVehicleId(3L);
        job.setJobStatus("PENDING");
        job.setItems(List.of());

        when(invoices.findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                10,
                InvoiceStatus.CANCELLED
        )).thenReturn(Optional.empty());

        when(adapter.getBillingDetails(10L)).thenReturn(job);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDraft(10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(invoices, never()).save(any(Invoice.class));
        verify(invoiceItems, never()).saveAll(anyList());
    }

    @Test
    void rejectsDraftCreationForInprogressJob() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                new InvoiceCalculationService(),
                invoices,
                invoiceItems
        );

        JobBillingDetails job = new JobBillingDetails();
        job.setServiceJobId(10L);
        job.setCustomerId(2L);
        job.setVehicleId(3L);
        job.setJobStatus("IN_PROGRESS");
        job.setItems(List.of());

        when(invoices.findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                10,
                InvoiceStatus.CANCELLED
        )).thenReturn(Optional.empty());

        when(adapter.getBillingDetails(10L)).thenReturn(job);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDraft(10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(invoices, never()).save(any(Invoice.class));
        verify(invoiceItems, never()).saveAll(anyList());
    }

    @Test
    void rejectsDraftCreationForCancelledJob() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                new InvoiceCalculationService(),
                invoices,
                invoiceItems
        );

        JobBillingDetails job = new JobBillingDetails();
        job.setServiceJobId(10L);
        job.setCustomerId(2L);
        job.setVehicleId(3L);
        job.setJobStatus("CANCELLED");
        job.setItems(List.of());

        when(invoices.findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                10,
                InvoiceStatus.CANCELLED
        )).thenReturn(Optional.empty());

        when(adapter.getBillingDetails(10L)).thenReturn(job);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDraft(10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(invoices, never()).save(any(Invoice.class));
        verify(invoiceItems, never()).saveAll(anyList());
    }

    @Test
    void rejectsDraftCreationForClosedJob() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                new InvoiceCalculationService(),
                invoices,
                invoiceItems
        );

        JobBillingDetails job = new JobBillingDetails();
        job.setServiceJobId(10L);
        job.setCustomerId(2L);
        job.setVehicleId(3L);
        job.setJobStatus("CLOSED");
        job.setItems(List.of());

        when(invoices.findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                10,
                InvoiceStatus.CANCELLED
        )).thenReturn(Optional.empty());

        when(adapter.getBillingDetails(10L)).thenReturn(job);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDraft(10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(invoices, never()).save(any(Invoice.class));
        verify(invoiceItems, never()).saveAll(anyList());
    }

    @Test
    void rejectsInvalidSearchInputsBeforeQueryingDatabase() {
        JobBillingAdapter adapter = mock(JobBillingAdapter.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItems =
                mock(InvoiceItemRepository.class);

        InvoiceService service = new InvoiceService(
                adapter,
                new InvoiceCalculationService(),
                invoices,
                invoiceItems
        );

        ResponseStatusException invalidPage = assertThrows(
                ResponseStatusException.class,
                () -> service.searchInvoices(
                        null, null, null, null, -1, 20
                )
        );

        ResponseStatusException invalidSize = assertThrows(
                ResponseStatusException.class,
                () -> service.searchInvoices(
                        null, null, null, null, 0, 101
                )
        );

        ResponseStatusException invalidCustomer = assertThrows(
                ResponseStatusException.class,
                () -> service.searchInvoices(
                        0L, null, null, null, 0, 20
                )
        );

        ResponseStatusException invalidJob = assertThrows(
                ResponseStatusException.class,
                () -> service.searchInvoices(
                        null, null, (long) Integer.MAX_VALUE + 1,
                        null, 0, 20
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, invalidPage.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidSize.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidCustomer.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidJob.getStatusCode());

        verifyNoInteractions(invoices, invoiceItems, adapter);
    }
}