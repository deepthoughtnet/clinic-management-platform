package com.deepthoughtnet.clinic.api.lab.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderPaymentRequest(
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        @NotNull BigDecimal amount,
        @NotNull PaymentMode paymentMode,
        @Size(max = 128) String referenceNumber,
        @Size(max = 1000) String notes,
        UUID receivedBy
) {
}
