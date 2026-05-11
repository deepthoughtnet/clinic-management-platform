package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicineRecord(
        UUID id,
        UUID tenantId,
        String medicineName,
        String medicineType,
        String genericName,
        String brandName,
        String category,
        String dosageForm,
        String strength,
        String unit,
        String manufacturer,
        String defaultDosage,
        String defaultFrequency,
        Integer defaultDurationDays,
        String defaultTiming,
        String defaultInstructions,
        BigDecimal defaultPrice,
        BigDecimal taxRate,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
