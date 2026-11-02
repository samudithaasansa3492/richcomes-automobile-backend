package com.richcomes.garage.billing.controller;

import com.richcomes.garage.billing.dto.InvoiceCalculationRequest;
import com.richcomes.garage.billing.dto.InvoiceCalculationResponse;
import com.richcomes.garage.billing.service.InvoiceCalculationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/billing")
public class InvoiceCalculationController {

    private final InvoiceCalculationService calculationService;

    public InvoiceCalculationController(
            InvoiceCalculationService calculationService
    ) {
        this.calculationService = calculationService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<InvoiceCalculationResponse> calculate(
            @Valid @RequestBody InvoiceCalculationRequest request
    ) {
        BigDecimal subtotal =
                calculationService.calculateSubtotal(request.getItems());

        return ResponseEntity.ok(
                new InvoiceCalculationResponse(subtotal)
        );
    }
}
