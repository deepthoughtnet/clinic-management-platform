package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationRecommendationItemResponse(
        String vaccineId,
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
    public static VaccinationRecommendationItemResponse from(com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationRecord record) {
        return new VaccinationRecommendationItemResponse(
                record.vaccineId() == null ? null : record.vaccineId().toString(),
                record.vaccineName(),
                record.brandName(),
                record.manufacturer(),
                record.vaccineGroup(),
                record.doseNumber(),
                record.route(),
                record.administrationSite(),
                record.scheduleType(),
                record.dueDate(),
                record.status(),
                record.overdueDays(),
                record.recommendedAgeDays(),
                record.patientAgeDays(),
                record.patientAgeGroup(),
                record.reasonText(),
                record.completedDate()
        );
    }
}
