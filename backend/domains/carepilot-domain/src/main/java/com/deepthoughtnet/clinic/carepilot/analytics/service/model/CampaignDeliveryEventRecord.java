package com.deepthoughtnet.clinic.carepilot.analytics.service.model;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only projection of persisted provider delivery lifecycle events.
 */
public record CampaignDeliveryEventRecord(
        UUID id,
        UUID tenantId,
        UUID executionId,
        UUID deliveryAttemptId,
        String providerName,
        String providerMessageId,
        ChannelType channelType,
        String externalStatus,
        MessageDeliveryStatus internalStatus,
        String eventType,
        OffsetDateTime eventTimestamp,
        OffsetDateTime receivedAt
) {}
