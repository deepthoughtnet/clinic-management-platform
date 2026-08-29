package com.deepthoughtnet.clinic.api.inventory;

import java.util.UUID;

record InventoryTransferRequest(
        UUID medicineId,
        UUID stockBatchId,
        UUID fromLocationId,
        UUID toLocationId,
        int quantity,
        String reason
) {
}
