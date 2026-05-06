package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentRecord(
        UUID id,
        UUID tenantId,
        UUID billId,
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes,
        UUID receiptId,
        String receiptNumber,
        LocalDate receiptDate,
        OffsetDateTime createdAt
) {
}
