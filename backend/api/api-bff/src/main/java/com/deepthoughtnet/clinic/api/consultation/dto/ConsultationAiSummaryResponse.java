package com.deepthoughtnet.clinic.api.consultation.dto;

import java.time.OffsetDateTime;

public record ConsultationAiSummaryResponse(
        String consultationId,
        String summary,
        String provider,
        String model,
        OffsetDateTime generatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
