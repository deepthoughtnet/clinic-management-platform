package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PatientVaccinationRequest(
        UUID vaccineId,
        @Size(max = 256)
        String vaccineName,
        @PositiveOrZero
        Integer doseNumber,
        @NotNull
        LocalDate givenDate,
        LocalDate nextDueDate,
        @Size(max = 60)
        String batchNumber,
        UUID stockBatchId,
        @Size(max = 250)
        String notes,
        @Size(max = 16)
        String source,
        @Size(max = 256)
        String externalPlace,
        UUID proofDocumentId,
        @Size(max = 32)
        String verifiedStatus,
        UUID administeredByUserId,
        UUID billId,
        boolean addToBill,
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal billItemUnitPrice,
        boolean inventoryOverride
) {
}
