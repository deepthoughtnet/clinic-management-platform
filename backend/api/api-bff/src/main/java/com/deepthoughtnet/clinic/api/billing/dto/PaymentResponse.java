package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentResponse(
        String id,
        String tenantId,
        String billId,
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes,
        String receiptId,
        String receiptNumber,
        LocalDate receiptDate,
        OffsetDateTime createdAt
) {
}
