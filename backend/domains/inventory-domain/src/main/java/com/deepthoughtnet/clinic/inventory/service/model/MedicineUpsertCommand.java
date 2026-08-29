package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MedicineUpsertCommand(
        @NotBlank
        @Size(min = 2, max = 60)
        @Pattern(regexp = ".*[A-Za-z0-9].*", message = "Medicine name must include a letter or number.")
        String medicineName,
        @NotBlank
        @Size(max = 24)
        @Pattern(regexp = "TABLET|CAPSULE|SYRUP|INJECTION|DROP|OINTMENT|SACHET|OTHER", message = "medicineType must be one of TABLET, CAPSULE, SYRUP, INJECTION, DROP, OINTMENT, SACHET, OTHER")
        String medicineType,
        @Size(max = 60)
        @Pattern(regexp = "^[A-Za-z0-9/_-]+$", message = "Barcode can use letters, numbers, dashes, underscores, and slashes only.")
        String barcode,
        @Size(max = 60)
        String qrCode,
        @Size(max = 60)
        String externalCode,
        @Size(max = 60)
        @Pattern(regexp = ".*[A-Za-z0-9].*", message = "Generic name must include a letter or number.")
        String genericName,
        @Size(max = 60)
        @Pattern(regexp = ".*[A-Za-z0-9].*", message = "Brand name must include a letter or number.")
        String brandName,
        @Size(max = 60)
        String category,
        @Size(max = 60)
        String dosageForm,
        @NotBlank
        @Size(max = 60)
        @Pattern(regexp = ".*[A-Za-z0-9].*", message = "Strength is required and must include a letter or number.")
        String strength,
        @Size(max = 60)
        String unit,
        @Size(max = 60)
        String manufacturer,
        @Size(max = 60)
        String defaultDosage,
        @Size(max = 60)
        String defaultFrequency,
        @Min(1)
        @Max(365)
        Integer defaultDurationDays,
        @Size(max = 24)
        @Pattern(regexp = "BEFORE_FOOD|AFTER_FOOD|WITH_FOOD|ANYTIME", message = "defaultTiming must be one of BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, ANYTIME")
        String defaultTiming,
        @Size(max = 250)
        String defaultInstructions,
        @DecimalMin(value = "0", inclusive = true)
        @DecimalMax(value = "999999.99", inclusive = true)
        @Digits(integer = 6, fraction = 2)
        BigDecimal defaultPrice,
        @DecimalMin(value = "0", inclusive = true)
        @DecimalMax(value = "100", inclusive = true)
        @Digits(integer = 3, fraction = 2)
        BigDecimal taxRate,
        boolean active
) {
}
