package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

public record BillRequest(
        @NotNull
        UUID patientId,
        UUID consultationId,
        UUID appointmentId,
        @NotNull @PastOrPresent
        LocalDate billDate,
        DiscountType discountType,
        BigDecimal discountValue,
        @PositiveOrZero
        BigDecimal taxAmount,
        @Size(max = 60)
        String discountReason,
        UUID discountApprovedBy,
        @Size(max = 250)
        String notes,
        @NotEmpty @Valid
        List<BillLineRequest> lines
) {
}
