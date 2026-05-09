package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicineUpsertCommand(
        @NotBlank
        @Size(max = 256)
        String medicineName,
        @NotBlank
        @Size(max = 64)
        String medicineType,
        @Size(max = 128)
        String strength,
        @Size(max = 128)
        String defaultDosage,
        @Size(max = 128)
        String defaultFrequency,
        Integer defaultDurationDays,
        @Size(max = 64)
        String defaultTiming,
        @Size(max = 512)
        String defaultInstructions,
        BigDecimal defaultPrice,
        boolean active
) {
}
