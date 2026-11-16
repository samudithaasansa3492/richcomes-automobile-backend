package com.richcomes.garage.billing.service;

import com.richcomes.garage.billing.dto.InvoiceItemResponse;
import com.richcomes.garage.billing.dto.InvoiceResponse;
import com.richcomes.garage.billing.dto.JobBillingDetails;
import com.richcomes.garage.billing.entity.Invoice;
import com.richcomes.garage.billing.entity.InvoiceItem;
import com.richcomes.garage.billing.repository.InvoiceItemRepository;
import com.richcomes.garage.billing.repository.InvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvoiceService {

    private final JobBillingAdapter jobBillingAdapter;
    private final InvoiceCalculationService calculationService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;

    public InvoiceService(
            JobBillingAdapter jobBillingAdapter,
            InvoiceCalculationService calculationService,
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository
    ) {
        this.jobBillingAdapter = jobBillingAdapter;
        this.calculationService = calculationService;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
    }

    @Transactional
    public InvoiceResponse createDraft(Long serviceJobId) {
        if (serviceJobId == null
                || serviceJobId <= 0
                || serviceJobId > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service job ID must be a positive 32-bit integer"
            );
        }

        JobBillingDetails job = jobBillingAdapter.getBillingDetails(serviceJobId);
        BigDecimal subtotal = calculationService.calculateSubtotal(job.getItems());

        Invoice invoice = invoiceRepository.save(new Invoice(
                Math.toIntExact(serviceJobId),
                job.getCustomerId(),
                job.getVehicleId(),
                LocalDateTime.now(),
                subtotal
        ));

        List<InvoiceItem> items = job.getItems().stream()
                .map(item -> new InvoiceItem(
                        invoice,
                        item.getItemType(),
                        item.getItemReferenceId(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        calculationService.calculateLineAmount(
                                item.getQuantity(), item.getUnitPrice()
                        )
                ))
                .toList();

        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(items);
        return toResponse(invoice, savedItems);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invoice not found"
                ));

        List<InvoiceItem> items =
                invoiceItemRepository.findByInvoice_IdOrderByIdAsc(invoiceId);

        return toResponse(invoice, items);
    }

    private InvoiceResponse toResponse(Invoice invoice, List<InvoiceItem> items) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setServiceJobId(invoice.getServiceJobId().longValue());
        response.setCustomerId(invoice.getCustomerId());
        response.setVehicleId(invoice.getVehicleId());
        response.setInvoiceNo(invoice.getInvoiceNo());
        response.setInvoiceDate(invoice.getInvoiceDate());
        response.setStatus(invoice.getStatus());
        response.setSubtotal(invoice.getSubtotal());
        response.setDiscountAmount(invoice.getDiscountAmount());
        response.setTotalAmount(invoice.getTotalAmount());
        response.setItems(items.stream().map(this::toItemResponse).toList());

        return response;
    }

    private InvoiceItemResponse toItemResponse(InvoiceItem item) {
        InvoiceItemResponse response = new InvoiceItemResponse();
        response.setId(item.getId());
        response.setItemType(item.getItemType());
        response.setItemReferenceId(item.getItemReferenceId());
        response.setDescription(item.getDescription());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setTotalAmount(item.getTotalAmount());

        return response;
    }
}
