package com.deepthoughtnet.clinic.api.lab.service.model;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderPaymentCommand(
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes,
        UUID receivedBy
) {
}
