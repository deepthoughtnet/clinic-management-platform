package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BillLineRequest(
        @NotNull
        BillItemType itemType,
        @NotBlank @Size(max = 100)
        String itemName,
        @Min(1) @NotNull
        Integer quantity,
        @PositiveOrZero @DecimalMax("999999.00")
        BigDecimal unitPrice,
        UUID referenceId,
        @PositiveOrZero @DecimalMax("999999.00")
        BigDecimal lineDiscountAmount,
        @Size(max = 128)
        String batchNumber,
        UUID dispensationReferenceId,
        @Min(1)
        Integer sortOrder
) {
}
