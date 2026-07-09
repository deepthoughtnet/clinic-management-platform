package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.util.UUID;

public record ClinicalReasoningRequest(
        UUID patientId,
        String chiefComplaint,
        String symptoms,
        String findings,
        String vitals,
        String diagnosis,
        String advice,
        String notes,
        String currentPrescriptionDraft,
        String labOrdersSummary
) {
}
