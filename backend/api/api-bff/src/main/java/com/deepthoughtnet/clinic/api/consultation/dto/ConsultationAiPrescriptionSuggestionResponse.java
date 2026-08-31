package com.deepthoughtnet.clinic.api.consultation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConsultationAiPrescriptionSuggestionResponse(
        String id,
        String consultationId,
        Integer versionNumber,
        String status,
        String sourceHash,
        String currentSourceHash,
        boolean stale,
        String summary,
        String rawText,
        boolean unstructured,
        String provider,
        String model,
        String generatedByAppUserId,
        String generatedByDisplayName,
        OffsetDateTime generatedAt,
        List<Item> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Item(
            String itemId,
            String medicine,
            String dose,
            String frequency,
            String duration,
            String reason,
            String safetyNote,
            String draftText,
            String status,
            String reviewedByAppUserId,
            String reviewedByDisplayName,
            OffsetDateTime reviewedAt
    ) {}
}
