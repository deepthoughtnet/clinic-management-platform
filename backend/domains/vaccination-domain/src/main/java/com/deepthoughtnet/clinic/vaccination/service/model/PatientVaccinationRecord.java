package com.deepthoughtnet.clinic.vaccination.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientVaccinationRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientNumber,
        String patientName,
        UUID vaccineId,
        String vaccineName,
        Integer doseNumber,
        LocalDate givenDate,
        LocalDate nextDueDate,
        String batchNumber,
        String notes,
        UUID administeredByUserId,
        String administeredByUserName,
        OffsetDateTime createdAt
) {
}
