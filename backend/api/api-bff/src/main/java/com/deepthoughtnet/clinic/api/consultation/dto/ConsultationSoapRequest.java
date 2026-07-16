package com.deepthoughtnet.clinic.api.consultation.dto;

public record ConsultationSoapRequest(
        String subjective,
        String objective,
        String assessment,
        String plan,
        String aiProvider,
        String aiModel,
        java.time.OffsetDateTime generatedAt
) {
}
