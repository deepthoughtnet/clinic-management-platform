package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes
) {
}
