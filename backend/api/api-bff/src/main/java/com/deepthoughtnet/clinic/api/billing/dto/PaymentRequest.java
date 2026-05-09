package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotNull
        LocalDate paymentDate,
        @NotNull @Positive
        BigDecimal amount,
        @NotNull
        PaymentMode paymentMode,
        @Size(max = 128)
        String referenceNumber,
        @Size(max = 4000)
        String notes
) {
}
