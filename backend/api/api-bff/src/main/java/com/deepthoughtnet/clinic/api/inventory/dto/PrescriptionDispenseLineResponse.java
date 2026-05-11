package com.deepthoughtnet.clinic.api.inventory.dto;

import java.util.UUID;

public record PrescriptionDispenseLineResponse(
        UUID itemId,
        String prescribedMedicineName,
        UUID medicineId,
        int prescribedQuantity,
        int dispensedQuantity,
        String status,
        Integer availableQuantity,
        UUID lastBatchId
) {
}
