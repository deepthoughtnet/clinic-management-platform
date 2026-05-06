package com.deepthoughtnet.clinic.inventory.service.model;

import java.util.UUID;

public record InventoryTransactionCommand(
        UUID medicineId,
        UUID stockBatchId,
        InventoryTransactionType transactionType,
        int quantity,
        String referenceType,
        UUID referenceId,
        String notes
) {
}
