package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiClinicalSummaryRequest(
        UUID patientId,
        String patientName,
        String historyText,
        String chronicHistory,
        String recentConsultationSummary,
        List<String> recentConsultations,
        List<String> currentMedications,
        List<String> allergies,
        String uploadedReportsSummary
) {
}
