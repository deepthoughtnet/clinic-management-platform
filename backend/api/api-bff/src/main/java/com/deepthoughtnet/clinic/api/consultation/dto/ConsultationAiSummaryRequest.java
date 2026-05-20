package com.deepthoughtnet.clinic.api.consultation.dto;

import java.time.OffsetDateTime;

public record ConsultationAiSummaryRequest(
        String summary,
        String provider,
        String model,
        OffsetDateTime generatedAt
) {
}
