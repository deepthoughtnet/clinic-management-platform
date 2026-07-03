package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiPrescriptionTemplateRequest(
        UUID consultationId,
        UUID patientId,
        String patientAgeGender,
        String vitals,
        String currentPrescriptionDraft,
        String labOrdersSummary,
        String diagnosis,
        String symptoms,
        String allergies,
        String currentMedications,
        String doctorNotes
) {
}
