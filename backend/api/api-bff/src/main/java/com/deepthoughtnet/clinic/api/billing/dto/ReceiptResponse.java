package com.deepthoughtnet.clinic.api.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ReceiptResponse(
        String id,
        String tenantId,
        String receiptNumber,
        String billId,
        String paymentId,
        LocalDate receiptDate,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
