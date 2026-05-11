package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MedicineUpsertCommand(
        @NotBlank
        @Size(max = 256)
        String medicineName,
        @NotBlank
        @Size(max = 64)
        String medicineType,
        @Size(max = 256)
        String genericName,
        @Size(max = 256)
        String brandName,
        @Size(max = 128)
        String category,
        @Size(max = 64)
        String dosageForm,
        @Size(max = 128)
        String strength,
        @Size(max = 32)
        String unit,
        @Size(max = 256)
        String manufacturer,
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
        BigDecimal taxRate,
        boolean active
) {
}
