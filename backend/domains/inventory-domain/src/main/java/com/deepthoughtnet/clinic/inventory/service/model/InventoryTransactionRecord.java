package com.deepthoughtnet.clinic.inventory.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryTransactionRecord(
        UUID id,
        UUID tenantId,
        UUID medicineId,
        UUID stockBatchId,
        InventoryTransactionType transactionType,
        int quantity,
        String referenceType,
        UUID referenceId,
        String notes,
        OffsetDateTime createdAt
) {
}
