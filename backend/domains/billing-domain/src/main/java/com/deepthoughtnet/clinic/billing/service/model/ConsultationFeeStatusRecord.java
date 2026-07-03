package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ConsultationFeeStatusRecord(
        String status,
        BigDecimal consultationFeeAmount,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        UUID billId,
        String billNumber
) {
}
