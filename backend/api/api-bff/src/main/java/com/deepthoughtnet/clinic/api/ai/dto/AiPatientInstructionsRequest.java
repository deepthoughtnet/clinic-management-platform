package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiPatientInstructionsRequest(
        UUID consultationId,
        UUID patientId,
        String diagnosis,
        String prescription,
        String instructionsContext,
        String language,
        String literacyLevel,
        String allergies,
        String warnings
) {
}
