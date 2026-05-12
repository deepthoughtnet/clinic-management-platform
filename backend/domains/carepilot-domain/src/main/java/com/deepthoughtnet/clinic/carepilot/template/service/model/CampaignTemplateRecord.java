package com.deepthoughtnet.clinic.carepilot.template.service.model;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for tenant template listings and details. */
public record CampaignTemplateRecord(
        UUID id,
        UUID tenantId,
        String name,
        ChannelType channelType,
        String subjectLine,
        String bodyTemplate,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
