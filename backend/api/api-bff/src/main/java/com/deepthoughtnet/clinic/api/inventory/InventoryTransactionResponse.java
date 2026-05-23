package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        UUID tenantId,
        UUID medicineId,
        UUID stockBatchId,
        UUID locationId,
        UUID targetLocationId,
        InventoryTransactionType transactionType,
        int quantity,
        Integer beforeQuantity,
        Integer afterQuantity,
        String reason,
        String referenceType,
        UUID referenceId,
        UUID createdBy,
        String notes,
        OffsetDateTime createdAt,
        String batchNumber,
        String adjustedByName,
        String businessReference
) {
}
