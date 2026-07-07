package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.util.List;

public record VaccinationRecommendationResponse(
        String patientId,
        String scheduleType,
        List<VaccinationRecommendationItemResponse> recommendedToday,
        List<VaccinationRecommendationItemResponse> overdue,
        List<VaccinationRecommendationItemResponse> upcoming,
        List<VaccinationRecommendationItemResponse> completed,
        List<VaccinationRecommendationItemResponse> optionalRiskBased,
        List<VaccinationRecommendationItemResponse> notApplicable
) {
}
