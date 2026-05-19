package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record InventoryTransferCommand(
        @NotNull
        UUID medicineId,
        UUID stockBatchId,
        @NotNull
        UUID fromLocationId,
        @NotNull
        UUID toLocationId,
        @Positive
        int quantity,
        String reason
) {
}
