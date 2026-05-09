package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BillLineRequest(
        @NotNull
        BillItemType itemType,
        @NotBlank @Size(max = 256)
        String itemName,
        @Min(1) @NotNull
        Integer quantity,
        @PositiveOrZero
        BigDecimal unitPrice,
        UUID referenceId,
        @Min(1)
        Integer sortOrder
) {
}
