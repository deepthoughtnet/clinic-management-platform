package com.deepthoughtnet.clinic.vaccination.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PatientVaccinationCommand(
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
