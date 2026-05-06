package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;

public record MedicineUpsertCommand(
        String medicineName,
        String medicineType,
        String strength,
        String defaultDosage,
        String defaultFrequency,
        Integer defaultDurationDays,
        String defaultTiming,
        String defaultInstructions,
        BigDecimal defaultPrice,
        boolean active
) {
}
