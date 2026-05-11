package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BillRequest(
        @NotNull
        UUID patientId,
        UUID consultationId,
        UUID appointmentId,
        LocalDate billDate,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal taxAmount,
        @Size(max = 1000)
        String discountReason,
        UUID discountApprovedBy,
        @Size(max = 4000)
        String notes,
        @NotEmpty @Valid
        List<BillLineRequest> lines
) {
}
