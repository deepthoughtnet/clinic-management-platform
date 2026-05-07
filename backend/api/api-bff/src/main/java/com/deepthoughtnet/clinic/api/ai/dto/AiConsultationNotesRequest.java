package com.deepthoughtnet.clinic.api.ai.dto;

import java.util.UUID;

public record AiConsultationNotesRequest(
        UUID consultationId,
        UUID patientId,
        String doctorNotes,
        String symptoms,
        String vitals,
        String observations
) {
}
