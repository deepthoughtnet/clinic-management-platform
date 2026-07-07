package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VaccineRequest(
        @NotBlank
        @Size(max = 100)
        String vaccineName,
        @Size(max = 250)
        String description,
        @Size(max = 250)
        String manufacturer,
        @Size(max = 250)
        String brandName,
        @Size(max = 128)
        String vaccineGroup,
        @PositiveOrZero
        Integer doseNumber,
        @Size(max = 32)
        String route,
        @Size(max = 128)
        String administrationSite,
        @Size(max = 128)
        String storageTemperature,
        @Size(max = 128)
        String ndcBarcode,
        @Size(max = 32)
        String scheduleType,
        @Size(max = 60)
        String ageGroup,
        @PositiveOrZero
        Integer minAgeDays,
        @PositiveOrZero
        Integer recommendedAgeDays,
        @PositiveOrZero
        Integer maxAgeDays,
        @PositiveOrZero
        Integer gapDays,
        @PositiveOrZero
        Integer recommendedGapDays,
        @PositiveOrZero
        Integer boosterGapDays,
        @Size(max = 500)
        String boosterRules,
        boolean recurring,
        @PositiveOrZero
        Integer recurrenceDays,
        @Size(max = 32)
        String recommendationPolicy,
        @Size(max = 32)
        String catchUpPolicy,
        @PositiveOrZero
        Integer catchUpMaxAgeDays,
        @Size(max = 32)
        String applicableAgeGroup,
        @Size(max = 1000)
        String clinicalIndications,
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal defaultPrice,
        boolean active
) {
}
