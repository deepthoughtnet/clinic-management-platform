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
        String reason,
        String referenceType,
        UUID referenceId,
        UUID createdBy,
        String notes,
        OffsetDateTime createdAt
) {
}
