package com.deepthoughtnet.clinic.api.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DispenseRequest(
        @NotBlank
        String prescribedMedicineName,
        UUID medicineId,
        @PositiveOrZero
        Integer quantity,
        UUID batchId,
        boolean allowBatchOverride,
        @Size(max = 24)
        String action
) {
}
