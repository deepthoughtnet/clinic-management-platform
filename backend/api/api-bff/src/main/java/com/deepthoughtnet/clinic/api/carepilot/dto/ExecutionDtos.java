package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ExecutionDtos {
    private ExecutionDtos() {}

    public record CreateExecutionRequest(
            UUID campaignId,
            UUID templateId,
            ChannelType channelType,
            UUID recipientPatientId,
            OffsetDateTime scheduledAt
    ) {}

    public record ExecutionResponse(
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
            String sourceType,
            UUID sourceReferenceId,
            String reminderWindow,
            OffsetDateTime referenceDateTime,
            OffsetDateTime lastAttemptAt,
            String failureReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record DeliveryAttemptResponse(
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
}
