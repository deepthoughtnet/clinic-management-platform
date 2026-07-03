package com.deepthoughtnet.clinic.api.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AiConsultationAskRequest(
        UUID consultationId,
        UUID patientId,
        @NotBlank @Size(max = 2000) String prompt,
        String patientAgeGender,
        String vitals,
        String allergies,
        String chronicConditions,
        String currentPrescriptionDraft,
        String labOrdersSummary,
        String chiefComplaints,
        String symptoms,
        String clinicalNotes,
        String diagnosis,
        String advice
) {
}
