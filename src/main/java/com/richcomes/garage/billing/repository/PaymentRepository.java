package com.richcomes.garage.billing.repository;

import com.richcomes.garage.billing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoice_IdOrderByPaymentDateAscIdAsc(Long invoiceId);

    @Query("""
            SELECT COALESCE(SUM(payment.amount), 0)
            FROM Payment payment
            WHERE payment.invoice.id = :invoiceId
            """)
    BigDecimal sumAmountByInvoiceId(
            @Param("invoiceId") Long invoiceId
    );
}