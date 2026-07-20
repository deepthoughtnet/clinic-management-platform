package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.OffsetDateTime;
import java.util.List;

/** DTOs used by the Jeevanam Engage ops console. */
public final class OpsConsoleDtos {
    private OpsConsoleDtos() {
    }

    public record OpsExecutionResponse(
            String executionId,
            String executionReference,
            String campaignReference,
            String campaignName,
            String campaignType,
            String patientReference,
            String patientName,
            String relatedEntityType,
            String relatedEntityLabel,
            ChannelType channelType,
            ExecutionStatus status,
            String deliveryStatus,
            String providerName,
            OffsetDateTime scheduledAt,
            OffsetDateTime queuedAt,
            long queueAgeMinutes,
            int attemptCount,
            int deliveryAttemptCount,
            int retryCount,
            String blockingReason,
            boolean stuck,
            String reminderWindow,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime lastAttemptAt,
            String failureReason,
            OffsetDateTime executedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record OpsReadinessResponse(
            boolean manualExecutionDispatcherEnabled,
            OffsetDateTime manualExecutionDispatcherLastAcquiredAt,
            OffsetDateTime manualExecutionDispatcherLastSkippedAt,
            boolean reminderSchedulerEnabled,
            OffsetDateTime reminderSchedulerLastScanAt,
            long queueDepth,
            long queuedCount,
            long processingCount,
            long retryingCount,
            long oldestQueuedAgeMinutes,
            OffsetDateTime lastSuccessfulDispatchAt,
            List<ProviderStatusResponse> providerStatuses
    ) {}
}
