package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiConsultationNotesRequest(
        UUID consultationId,
        UUID patientId,
        String patientAgeGender,
        String chiefComplaint,
        String allergies,
        String chronicConditions,
        String currentPrescriptionDraft,
        String labOrdersSummary,
        String doctorNotes,
        String symptoms,
        String diagnosis,
        String advice,
        String vitals,
        String observations
) {
}
