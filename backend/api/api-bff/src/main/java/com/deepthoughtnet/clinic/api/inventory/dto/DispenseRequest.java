package com.deepthoughtnet.clinic.api.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DispenseRequest(
        UUID medicineLineId,
        @NotBlank
        String prescribedMedicineName,
        UUID medicineId,
        @Positive
        Integer quantity,
        @Size(max = 60)
        String batchOverride,
        boolean allowBatchOverride,
        @Size(max = 24)
        String action,
        @Size(max = 60)
        String reason,
        @Size(max = 250)
        String remarks
) {
}
