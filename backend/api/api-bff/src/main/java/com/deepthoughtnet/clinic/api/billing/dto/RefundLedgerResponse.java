package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-only refund ledger row enriched with bill and patient context.
 */
public record RefundLedgerResponse(
        String id,
        String tenantId,
        String billId,
        String billNumber,
        String patientId,
        String patientName,
        String patientNumber,
        String paymentId,
        BigDecimal amount,
        String reason,
        PaymentMode refundMode,
        String refundedBy,
        OffsetDateTime refundedAt,
        String notes,
        BillStatus billStatus,
        BigDecimal billDueAmount,
        OffsetDateTime createdAt
) {
}
