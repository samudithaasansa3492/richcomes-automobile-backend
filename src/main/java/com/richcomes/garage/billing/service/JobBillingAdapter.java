package com.richcomes.garage.billing.service;

import com.richcomes.garage.billing.dto.InvoiceItemRequest;
import com.richcomes.garage.billing.dto.JobBillingDetails;
import com.richcomes.garage.billing.enums.InvoiceItemType;
import com.richcomes.garage.inventory.stockitem.service.StockItemService;
import com.richcomes.garage.service.dto.ServiceJobDTO;
import com.richcomes.garage.service.dto.ServiceJobItemDTO;
import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import com.richcomes.garage.service.service.ServiceItemService;
import com.richcomes.garage.service.service.ServiceJobItemService;
import com.richcomes.garage.service.service.ServiceJobPartService;
import com.richcomes.garage.service.service.ServiceJobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobBillingAdapter {

    private final ServiceJobService jobService;
    private final ServiceJobItemService jobItemService;
    private final ServiceJobPartService jobPartService;
    private final ServiceItemService serviceItemService;
    private final StockItemService stockItemService;
    private final InvoiceCalculationService calculationService;

    public JobBillingAdapter(
            ServiceJobService jobService,
            ServiceJobItemService jobItemService,
            ServiceJobPartService jobPartService,
            ServiceItemService serviceItemService,
            StockItemService stockItemService,
            InvoiceCalculationService calculationService
    ) {
        this.jobService = jobService;
        this.jobItemService = jobItemService;
        this.jobPartService = jobPartService;
        this.serviceItemService = serviceItemService;
        this.stockItemService = stockItemService;
        this.calculationService = calculationService;
    }

    @Transactional(readOnly = true)
    public JobBillingDetails getBillingDetails(Long jobId) {
        requirePositiveId(jobId, "Service job ID");

        ServiceJobDTO job = jobService.getJobById(jobId);

        if (job == null || !jobId.equals(job.getId())) {
            throw new IllegalStateException(
                    "Job service returned invalid job details"
            );
        }

        requirePositiveId(job.getCustomerId(), "Job customer ID");
        requirePositiveId(job.getVehicleId(), "Job vehicle ID");

        List<InvoiceItemRequest> items = new ArrayList<>();

        List<ServiceJobItemDTO> labourLines =
                jobItemService.getItemsByJobId(jobId);

        if (labourLines == null) {
            throw new IllegalStateException(
                    "Job service returned a null labour list"
            );
        }

        for (ServiceJobItemDTO labour : labourLines) {
            if (labour == null) {
                throw new IllegalStateException(
                        "Job contains a null labour line"
                );
            }

            requireMatchingJob(jobId, labour.getServiceJobId());
            requirePositiveId(
                    labour.getServiceItemId(), "Service item ID"
            );

            String description = serviceItemService
                    .getServiceItemById(labour.getServiceItemId())
                    .getName();

            items.add(createLine(
                    InvoiceItemType.LABOUR,
                    labour.getServiceItemId(),
                    description,
                    labour.getLabourHours(),
                    labour.getLabourRate()
            ));
        }

        List<ServiceJobPartDTO> partLines =
                jobPartService.getPartsByJobId(jobId);

        if (partLines == null) {
            throw new IllegalStateException(
                    "Job service returned a null parts list"
            );
        }

        for (ServiceJobPartDTO part : partLines) {
            if (part == null) {
                throw new IllegalStateException(
                        "Job contains a null part line"
                );
            }

            requireMatchingJob(jobId, part.getServiceJobId());
            requirePositiveId(part.getPartId(), "Stock item ID");

            String description = stockItemService
                    .getItemById(part.getPartId())
                    .getItemName();

            items.add(createLine(
                    InvoiceItemType.PART,
                    part.getPartId(),
                    description,
                    part.getQuantity(),
                    part.getUnitPrice()
            ));
        }

        if (items.isEmpty()) {
            throw new IllegalStateException(
                    "Job has no billable service or part lines"
            );
        }

        JobBillingDetails details = new JobBillingDetails();
        details.setServiceJobId(job.getId());
        details.setCustomerId(job.getCustomerId());
        details.setVehicleId(job.getVehicleId());
        details.setJobNo(job.getJobNo());
        details.setJobStatus(job.getStatus());
        details.setItems(List.copyOf(items));

        return details;
    }

    private InvoiceItemRequest createLine(
            InvoiceItemType type,
            Long referenceId,
            String description,
            Double quantity,
            BigDecimal unitPrice
    ) {
        if (description == null || description.isBlank()) {
            throw new IllegalStateException(
                    "Invoice line description is missing"
            );
        }

        String trimmedDescription = description.trim();

        if (trimmedDescription.length() > 255) {
            throw new IllegalStateException(
                    "Invoice line description exceeds 255 characters"
            );
        }

        if (quantity == null
                || !Double.isFinite(quantity)
                || quantity <= 0) {
            throw new IllegalStateException(
                    "Job line quantity must be finite and positive"
            );
        }

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalStateException(
                    "Job line price must be present and nonnegative"
            );
        }

        BigDecimal decimalQuantity = BigDecimal.valueOf(quantity);

        // Validate using the existing calculation rules.
        calculationService.calculateLineAmount(
                decimalQuantity, unitPrice
        );

        InvoiceItemRequest item = new InvoiceItemRequest();
        item.setItemType(type);
        item.setItemReferenceId(referenceId);
        item.setDescription(trimmedDescription);
        item.setQuantity(decimalQuantity);
        item.setUnitPrice(unitPrice);

        return item;
    }

    private void requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }

    private void requireMatchingJob(Long expectedId, Long actualId) {
        if (!expectedId.equals(actualId)) {
            throw new IllegalStateException(
                    "Invoice line belongs to a different job"
            );
        }
    }
}
