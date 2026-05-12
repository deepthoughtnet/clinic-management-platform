package com.deepthoughtnet.clinic.carepilot.execution.service.model;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model for execution delivery-attempt history.
 */
public record CampaignDeliveryAttemptRecord(
        UUID id,
        UUID tenantId,
        UUID executionId,
        int attemptNumber,
        String providerName,
        ChannelType channelType,
        MessageDeliveryStatus deliveryStatus,
        String errorCode,
        String errorMessage,
        OffsetDateTime attemptedAt
) {}
