package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record InventoryTransactionCommand(
        @NotNull
        UUID medicineId,
        UUID stockBatchId,
        @NotNull
        InventoryTransactionType transactionType,
        @Positive
        int quantity,
        String reason,
        String referenceType,
        UUID referenceId,
        UUID createdBy,
        String notes
) {
}
