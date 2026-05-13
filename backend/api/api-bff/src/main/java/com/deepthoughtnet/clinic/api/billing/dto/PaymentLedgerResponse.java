package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Read-only payment ledger row enriched with bill and patient context.
 */
public record PaymentLedgerResponse(
        String id,
        String tenantId,
        String billId,
        String billNumber,
        String patientId,
        String patientName,
        String patientNumber,
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes,
        String receivedBy,
        String receiptId,
        String receiptNumber,
        LocalDate receiptDate,
        BillStatus billStatus,
        BigDecimal billDueAmount,
        OffsetDateTime createdAt
) {
}
