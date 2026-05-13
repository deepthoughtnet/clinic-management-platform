package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.DeliveryAttemptResponse;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.ExecutionResponse;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response DTOs for CarePilot analytics and ops endpoints. */
public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record CampaignBreakdownResponse(
            UUID campaignId,
            String campaignName,
            long totalExecutions,
            long successfulExecutions,
            long failedExecutions,
            double successRate
    ) {}

    public record ProviderFailureSummaryResponse(
            String providerName,
            long failureCount
    ) {}

    public record AnalyticsSummaryResponse(
            LocalDate startDate,
            LocalDate endDate,
            long totalCampaigns,
            long activeCampaigns,
            long totalExecutions,
            long pendingExecutions,
            long scheduledExecutions,
            long successfulExecutions,
            long failedExecutions,
            long retryingExecutions,
            long skippedExecutions,
            long deliveredExecutions,
            long readExecutions,
            long bouncedExecutions,
            long undeliveredExecutions,
            double successRate,
            double failureRate,
            double retryRate,
            Map<String, Long> executionsByStatus,
            Map<String, Long> executionsByChannel,
            List<CampaignBreakdownResponse> executionsByCampaign,
            List<ProviderFailureSummaryResponse> providerFailureSummary,
            List<ExecutionResponse> recentFailures,
            List<ExecutionResponse> recentSuccesses
    ) {}

    public record TimelineEventResponse(
            String type,
            String status,
            String detail,
            OffsetDateTime at
    ) {}

    public record ExecutionTimelineResponse(
            ExecutionResponse execution,
            List<DeliveryAttemptResponse> deliveryAttempts,
            List<DeliveryEventResponse> deliveryEvents,
            List<TimelineEventResponse> statusEvents
    ) {}

    public record DeliveryEventResponse(
            String id,
            String executionId,
            String providerName,
            String providerMessageId,
            ChannelType channelType,
            String externalStatus,
            MessageDeliveryStatus internalStatus,
            String eventType,
            OffsetDateTime eventTimestamp,
            OffsetDateTime receivedAt
    ) {}

    public record FailedExecutionFilter(
            LocalDate startDate,
            LocalDate endDate,
            UUID campaignId,
            ChannelType channel,
            ExecutionStatus status,
            String providerName,
            Boolean retryableOnly
    ) {}
}
