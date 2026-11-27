package com.richcomes.garage.billing.service;

import com.richcomes.garage.billing.dto.InvoiceItemResponse;
import com.richcomes.garage.billing.dto.InvoiceResponse;
import com.richcomes.garage.billing.dto.JobBillingDetails;
import com.richcomes.garage.billing.entity.Invoice;
import com.richcomes.garage.billing.entity.InvoiceItem;
import com.richcomes.garage.billing.repository.InvoiceItemRepository;
import com.richcomes.garage.billing.repository.InvoiceRepository;
import com.richcomes.garage.billing.enums.InvoiceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;


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

        Integer jobId = Math.toIntExact(serviceJobId);

        var existingInvoice = invoiceRepository
                .findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
                        jobId,
                        InvoiceStatus.CANCELLED
                );

        if (existingInvoice.isPresent()) {
            Invoice invoice = existingInvoice.get();

            List<InvoiceItem> items =
                    invoiceItemRepository.findByInvoice_IdOrderByIdAsc(
                            invoice.getId()
                    );

            return toResponse(invoice, items);
        }

        JobBillingDetails job =
                jobBillingAdapter.getBillingDetails(serviceJobId);

        if (job.getJobStatus() == null
                || !"COMPLETED".equalsIgnoreCase(job.getJobStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invoice can only be created for a completed service job"
            );
        }

        BigDecimal subtotal =
                calculationService.calculateSubtotal(job.getItems());

        Invoice invoice = invoiceRepository.save(new Invoice(
                jobId,
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
                                item.getQuantity(),
                                item.getUnitPrice()
                        )
                ))
                .toList();

        List<InvoiceItem> savedItems =
                invoiceItemRepository.saveAll(items);

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

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> searchInvoices(
            Long customerId,
            Long vehicleId,
            Long serviceJobId,
            InvoiceStatus status,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater and size must be between 1 and 100"
            );
        }

        if ((customerId != null && customerId <= 0)
                || (vehicleId != null && vehicleId <= 0)
                || (serviceJobId != null
                && (serviceJobId <= 0
                || serviceJobId > Integer.MAX_VALUE))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Filter IDs must be positive and service job ID must fit a 32-bit integer"
            );
        }

        Specification<Invoice> specification = (root, query, builder) -> {
            List<Predicate> filters = new ArrayList<>();

            if (customerId != null) {
                filters.add(builder.equal(root.get("customerId"), customerId));
            }

            if (vehicleId != null) {
                filters.add(builder.equal(root.get("vehicleId"), vehicleId));
            }

            if (serviceJobId != null) {
                filters.add(builder.equal(
                        root.get("serviceJobId"),
                        Math.toIntExact(serviceJobId)
                ));
            }

            if (status != null) {
                filters.add(builder.equal(root.get("status"), status));
            }

            return builder.and(filters.toArray(Predicate[]::new));
        };

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        return invoiceRepository.findAll(specification, pageRequest)
                .map(invoice -> toResponse(
                        invoice,
                        invoiceItemRepository.findByInvoice_IdOrderByIdAsc(
                                invoice.getId()
                        )
                ));
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
