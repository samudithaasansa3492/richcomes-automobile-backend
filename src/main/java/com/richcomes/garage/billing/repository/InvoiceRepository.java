package com.richcomes.garage.billing.repository;

import com.richcomes.garage.billing.entity.Invoice;
import com.richcomes.garage.billing.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InvoiceRepository extends
        JpaRepository<Invoice, Long>,
        JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findFirstByServiceJobIdAndStatusNotOrderByIdDesc(
            Integer serviceJobId,
            InvoiceStatus status
    );
}
