package com.deepthoughtnet.clinic.api.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record DispenseRequest(
        @NotBlank
        String prescribedMedicineName,
        UUID medicineId,
        @Positive
        int quantity,
        UUID batchId,
        boolean allowBatchOverride
) {
}
