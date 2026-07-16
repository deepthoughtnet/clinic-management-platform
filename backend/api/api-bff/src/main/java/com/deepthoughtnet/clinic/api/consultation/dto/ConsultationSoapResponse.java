package com.deepthoughtnet.clinic.api.consultation.dto;

import java.time.OffsetDateTime;

public record ConsultationSoapResponse(
        String id,
        String consultationId,
        Integer versionNumber,
        String status,
        String source,
        String subjective,
        String objective,
        String assessment,
        String plan,
        String aiProvider,
        String aiModel,
        String generatedByAppUserId,
        String acceptedByAppUserId,
        String sourceHash,
        String currentSourceHash,
        boolean stale,
        OffsetDateTime generatedAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
