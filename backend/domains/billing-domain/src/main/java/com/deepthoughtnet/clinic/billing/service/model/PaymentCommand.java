package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentCommand(
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes
) {
}
