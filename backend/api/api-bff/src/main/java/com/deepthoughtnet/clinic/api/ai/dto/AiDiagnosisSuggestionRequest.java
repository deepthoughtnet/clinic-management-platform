package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiDiagnosisSuggestionRequest(
        UUID consultationId,
        UUID patientId,
        String patientAgeGender,
        String vitals,
        String currentPrescriptionDraft,
        String labOrdersSummary,
        String symptoms,
        String findings,
        String doctorNotes,
        String knownConditions,
        String allergies
) {
}
