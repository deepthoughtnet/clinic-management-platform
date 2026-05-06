package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicineRecord(
        UUID id,
        UUID tenantId,
        String medicineName,
        String medicineType,
        String strength,
        String defaultDosage,
        String defaultFrequency,
        Integer defaultDurationDays,
        String defaultTiming,
        String defaultInstructions,
        BigDecimal defaultPrice,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
