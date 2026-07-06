package com.deepthoughtnet.clinic.api.lab.service.model;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabPaymentReceiptRecord(
        UUID receiptId,
        String receiptNumber,
        UUID billId,
        String billNumber,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String collectedBy,
        OffsetDateTime collectedAt,
        String printUrl,
        String downloadUrl
) {
    public LocalDate collectedDate() {
        return collectedAt == null ? null : collectedAt.toLocalDate();
    }
}
