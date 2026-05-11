package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundResponse(
        String id,
        String billId,
        String paymentId,
        String tenantId,
        BigDecimal amount,
        String reason,
        PaymentMode refundMode,
        String refundedBy,
        OffsetDateTime refundedAt,
        String notes,
        OffsetDateTime createdAt
) {
}
