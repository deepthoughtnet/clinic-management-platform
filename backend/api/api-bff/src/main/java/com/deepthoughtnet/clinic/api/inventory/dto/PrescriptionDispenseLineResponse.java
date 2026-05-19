package com.deepthoughtnet.clinic.api.inventory.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PrescriptionDispenseLineResponse(
        UUID itemId,
        String prescribedMedicineName,
        UUID medicineId,
        int prescribedQuantity,
        int dispensedQuantity,
        int pendingQuantity,
        String status,
        Integer availableQuantity,
        String availabilityStatus,
        String expiryStatus,
        LocalDate nearestExpiryDate,
        UUID lastBatchId
) {
}
