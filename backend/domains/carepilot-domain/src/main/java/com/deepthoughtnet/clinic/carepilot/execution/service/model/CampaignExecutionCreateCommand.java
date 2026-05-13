package com.deepthoughtnet.clinic.carepilot.execution.service.model;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Command used to stage a campaign execution row. */
public record CampaignExecutionCreateCommand(
        UUID campaignId,
        UUID templateId,
        ChannelType channelType,
        UUID recipientPatientId,
        OffsetDateTime scheduledAt,
        String sourceType,
        UUID sourceReferenceId,
        String reminderWindow,
        OffsetDateTime referenceDateTime
) {}
