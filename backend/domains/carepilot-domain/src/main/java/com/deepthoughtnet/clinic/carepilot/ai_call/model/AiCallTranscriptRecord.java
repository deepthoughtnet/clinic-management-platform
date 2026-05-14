package com.deepthoughtnet.clinic.carepilot.ai_call.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for persisted AI call transcript foundation data. */
public record AiCallTranscriptRecord(
        UUID id,
        UUID tenantId,
        UUID executionId,
        String transcriptText,
        String summary,
        String sentiment,
        String outcome,
        String intent,
        boolean requiresFollowUp,
        String escalationReason,
        String extractedEntitiesJson,
        OffsetDateTime createdAt
) {}
