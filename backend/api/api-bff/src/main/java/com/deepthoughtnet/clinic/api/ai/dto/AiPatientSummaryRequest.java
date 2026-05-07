package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiPatientSummaryRequest(
        UUID patientId,
        String patientName,
        String historyText,
        String activeConditions,
        String currentMedications,
        String allergies,
        String recentVisits
) {
}
