package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReceiptRecord(
        UUID id,
        UUID tenantId,
        String receiptNumber,
        UUID billId,
        UUID paymentId,
        LocalDate receiptDate,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
