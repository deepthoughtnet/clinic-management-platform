package com.deepthoughtnet.clinic.vaccination.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PatientVaccinationCommand(
        UUID vaccineId,
        String vaccineName,
        Integer doseNumber,
        LocalDate givenDate,
        LocalDate nextDueDate,
        String batchNumber,
        UUID stockBatchId,
        String notes,
        String source,
        String externalPlace,
        UUID proofDocumentId,
        String verifiedStatus,
        UUID administeredByUserId,
        UUID billId,
        boolean addToBill,
        BigDecimal billItemUnitPrice,
        boolean inventoryOverride
) {
}
