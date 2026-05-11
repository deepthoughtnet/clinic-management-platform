package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCommand(
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes,
        UUID receivedBy
) {
}
