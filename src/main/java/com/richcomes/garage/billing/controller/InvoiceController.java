package com.richcomes.garage.billing.controller;

import com.richcomes.garage.billing.dto.InvoiceResponse;
import com.richcomes.garage.billing.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.richcomes.garage.billing.dto.InvoiceCreateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.richcomes.garage.billing.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/billing/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @PostMapping("/drafts")
    public ResponseEntity<InvoiceResponse> createDraft(
            @Valid @RequestBody InvoiceCreateRequest request
    ) {
        return ResponseEntity.ok(
                invoiceService.createDraft(request.getServiceJobId())
        );
    }

    @GetMapping
    public ResponseEntity<Page<InvoiceResponse>> searchInvoices(
            @RequestParam(name = "customerId", required = false)
            Long customerId,

            @RequestParam(name = "vehicleId", required = false)
            Long vehicleId,

            @RequestParam(name = "serviceJobId", required = false)
            Long serviceJobId,

            @RequestParam(name = "status", required = false)
            InvoiceStatus status,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        return ResponseEntity.ok(
                invoiceService.searchInvoices(
                        customerId,
                        vehicleId,
                        serviceJobId,
                        status,
                        page,
                        size
                )
        );
    }
}
