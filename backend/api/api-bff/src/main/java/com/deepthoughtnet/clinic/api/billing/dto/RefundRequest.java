package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundRequest(
        UUID paymentId,
        @NotNull @Positive
        BigDecimal amount,
        @NotNull @Size(min = 1, max = 1000)
        String reason,
        PaymentMode refundMode,
        OffsetDateTime refundedAt,
        @Size(max = 4000)
        String notes
) {
}
