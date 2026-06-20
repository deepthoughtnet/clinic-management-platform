package com.deepthoughtnet.clinic.api.lab.dto;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderPaymentRequest(
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @DecimalMax(value = "999999.00", inclusive = true) @Digits(integer = 6, fraction = 2) BigDecimal amount,
        @NotNull PaymentMode paymentMode,
        @Size(max = 60) String referenceNumber,
        @Size(max = 250) String notes,
        UUID receivedBy
) {
}
