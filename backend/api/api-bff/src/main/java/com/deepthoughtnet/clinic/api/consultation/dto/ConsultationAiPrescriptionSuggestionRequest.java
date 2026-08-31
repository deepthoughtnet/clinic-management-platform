package com.deepthoughtnet.clinic.api.consultation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConsultationAiPrescriptionSuggestionRequest(
        String summary,
        String rawText,
        boolean unstructured,
        String provider,
        String model,
        OffsetDateTime generatedAt,
        List<Item> items
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
            String status
    ) {}
}
