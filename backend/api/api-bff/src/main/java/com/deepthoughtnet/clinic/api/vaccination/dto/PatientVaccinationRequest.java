package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PatientVaccinationRequest(
        UUID vaccineId,
        Integer doseNumber,
        LocalDate givenDate,
        LocalDate nextDueDate,
        String batchNumber,
        String notes,
        UUID administeredByUserId,
        UUID billId,
        boolean addToBill,
        BigDecimal billItemUnitPrice
) {
}
