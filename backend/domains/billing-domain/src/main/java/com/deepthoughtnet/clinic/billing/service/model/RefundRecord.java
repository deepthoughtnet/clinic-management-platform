package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundRecord(
        UUID id,
        UUID tenantId,
        UUID billId,
        UUID paymentId,
        BigDecimal amount,
        String reason,
        PaymentMode refundMode,
        String notes,
        UUID refundedBy,
        OffsetDateTime refundedAt,
        OffsetDateTime createdAt
) {
}
