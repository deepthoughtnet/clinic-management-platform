package com.deepthoughtnet.clinic.carepilot.execution.service.model;

import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model of execution ledger state. */
public record CampaignExecutionRecord(
        UUID id,
        UUID tenantId,
        UUID campaignId,
        UUID templateId,
        ChannelType channelType,
        UUID recipientPatientId,
        OffsetDateTime scheduledAt,
        ExecutionStatus status,
        int attemptCount,
        String lastError,
        OffsetDateTime executedAt,
        OffsetDateTime nextAttemptAt,
        MessageDeliveryStatus deliveryStatus,
        String providerName,
        String providerMessageId,
        OffsetDateTime lastAttemptAt,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
