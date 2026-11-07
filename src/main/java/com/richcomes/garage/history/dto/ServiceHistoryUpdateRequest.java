package com.richcomes.garage.history.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ServiceHistoryUpdateRequest {

    @Min(value = 0, message = "Odometer cannot be negative")
    private Integer odometer;

    @Pattern(
            regexp = ".*\\S.*",
            message = "Summary cannot be blank"
    )
    private String summary;

    @Positive(message = "Invoice ID must be positive")
    private Long invoiceId;

    @DecimalMin(value = "0.0", inclusive = true, message = "Total cost cannot be negative")
    private BigDecimal totalCost;

    private LocalDate nextServiceDate;

    public ServiceHistoryUpdateRequest() {
    }

    public Integer getOdometer() {
        return odometer;
    }

    public void setOdometer(Integer odometer) {
        this.odometer = odometer;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public LocalDate getNextServiceDate() {
        return nextServiceDate;
    }

    public void setNextServiceDate(LocalDate nextServiceDate) {
        this.nextServiceDate = nextServiceDate;
    }
}
