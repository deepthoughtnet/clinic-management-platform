package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PatientVaccinationRequest(
        @NotNull
        UUID vaccineId,
        @PositiveOrZero
        Integer doseNumber,
        @NotNull
        LocalDate givenDate,
        LocalDate nextDueDate,
        @Size(max = 60)
        String batchNumber,
        @Size(max = 250)
        String notes,
        UUID administeredByUserId,
        UUID billId,
        boolean addToBill,
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal billItemUnitPrice
) {
}
