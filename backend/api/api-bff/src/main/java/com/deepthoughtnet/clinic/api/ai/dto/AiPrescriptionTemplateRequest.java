package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiPrescriptionTemplateRequest(
        UUID consultationId,
        UUID patientId,
        String diagnosis,
        String symptoms,
        String allergies,
        String currentMedications,
        String doctorNotes
) {
}
