package com.deepthoughtnet.clinic.vaccination.service.model;

import java.util.List;

public record VaccinationRecommendationSummary(
        String patientId,
        String scheduleType,
        List<VaccinationRecommendationRecord> recommendedToday,
        List<VaccinationRecommendationRecord> overdue,
        List<VaccinationRecommendationRecord> upcoming,
        List<VaccinationRecommendationRecord> completed,
        List<VaccinationRecommendationRecord> optionalRiskBased,
        List<VaccinationRecommendationRecord> notApplicable
) {
}
