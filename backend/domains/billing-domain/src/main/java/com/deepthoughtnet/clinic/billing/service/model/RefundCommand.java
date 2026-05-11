package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundCommand(
        UUID paymentId,
        BigDecimal amount,
        String reason,
        PaymentMode refundMode,
        String notes,
        OffsetDateTime refundedAt
) {
}
