package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VaccineRequest(
        @NotBlank
        @Size(max = 100)
        String vaccineName,
        @Size(max = 250)
        String description,
        @Size(max = 60)
        String ageGroup,
        @PositiveOrZero
        Integer recommendedGapDays,
        @NotNull
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal defaultPrice,
        boolean active
) {
}
