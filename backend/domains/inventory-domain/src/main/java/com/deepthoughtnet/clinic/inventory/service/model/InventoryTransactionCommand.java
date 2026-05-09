package com.deepthoughtnet.clinic.inventory.service.model;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryTransactionCommand(
        @NotNull
        UUID medicineId,
        UUID stockBatchId,
        @NotNull
        InventoryTransactionType transactionType,
        @Positive
        int quantity,
        String referenceType,
        UUID referenceId,
        String notes
) {
}
