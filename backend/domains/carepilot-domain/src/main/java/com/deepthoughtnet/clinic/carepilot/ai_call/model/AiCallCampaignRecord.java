package com.deepthoughtnet.clinic.carepilot.ai_call.model;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for AI call campaign configuration. */
public record AiCallCampaignRecord(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        AiCallType callType,
        AiCallCampaignStatus status,
        UUID templateId,
        ChannelType channel,
        boolean retryEnabled,
        int maxAttempts,
        boolean escalationEnabled,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
