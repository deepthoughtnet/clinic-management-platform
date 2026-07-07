package com.deepthoughtnet.clinic.vaccination.service.model;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationRecommendationRecord(
        UUID vaccineId,
        String vaccineName,
        String brandName,
        String manufacturer,
        String vaccineGroup,
        Integer doseNumber,
        String route,
        String administrationSite,
        String scheduleType,
        LocalDate dueDate,
        String status,
        Integer overdueDays,
        Integer recommendedAgeDays,
        Integer patientAgeDays,
        String patientAgeGroup,
        String reasonText,
        LocalDate completedDate
) {
}
