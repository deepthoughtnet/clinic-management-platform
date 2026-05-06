package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PatientVaccinationResponse(
        String id,
        String tenantId,
        String patientId,
        String patientNumber,
        String patientName,
        String vaccineId,
        String vaccineName,
        Integer doseNumber,
        LocalDate givenDate,
        LocalDate nextDueDate,
        String batchNumber,
        String notes,
        String administeredByUserId,
        String administeredByUserName,
        OffsetDateTime createdAt
) {
}
