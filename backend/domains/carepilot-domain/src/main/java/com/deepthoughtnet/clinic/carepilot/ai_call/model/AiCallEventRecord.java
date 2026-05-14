package com.deepthoughtnet.clinic.carepilot.ai_call.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for AI call execution event history rows. */
public record AiCallEventRecord(
        UUID id,
        UUID tenantId,
        UUID executionId,
        String providerName,
        String providerCallId,
        AiCallEventType eventType,
        String externalStatus,
        AiCallExecutionStatus internalStatus,
        OffsetDateTime eventTimestamp,
        String rawPayloadRedacted,
        OffsetDateTime createdAt
) {}
