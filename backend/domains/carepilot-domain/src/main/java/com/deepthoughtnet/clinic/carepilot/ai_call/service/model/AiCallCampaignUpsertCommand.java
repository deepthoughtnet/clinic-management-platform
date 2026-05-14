package com.deepthoughtnet.clinic.carepilot.ai_call.service.model;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.util.UUID;

/** Input command for AI call campaign create/update. */
public record AiCallCampaignUpsertCommand(
        String name,
        String description,
        AiCallType callType,
        AiCallCampaignStatus status,
        UUID templateId,
        ChannelType channel,
        Boolean retryEnabled,
        Integer maxAttempts,
        Boolean escalationEnabled
) {}
