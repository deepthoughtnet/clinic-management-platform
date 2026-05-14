package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.ai_call.analytics.AiCallAnalyticsRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallEventType;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs for CarePilot AI calls campaign/orchestration APIs. */
public final class AiCallDtos {
    private AiCallDtos() {}

    public record AiCallCampaignResponse(
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

    public record AiCallCampaignUpsertRequest(
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

    public record AiCallCampaignStatusRequest(AiCallCampaignStatus status) {}

    public record AiCallExecutionResponse(
            UUID id,
            UUID tenantId,
            UUID campaignId,
            UUID patientId,
            UUID leadId,
            String phoneNumber,
            AiCallExecutionStatus executionStatus,
            String providerName,
            String providerCallId,
            OffsetDateTime scheduledAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            int retryCount,
            OffsetDateTime nextRetryAt,
            OffsetDateTime lastAttemptAt,
            String failureReason,
            String suppressionReason,
            boolean escalationRequired,
            String escalationReason,
            boolean failoverAttempted,
            String failoverReason,
            UUID transcriptId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            long durationSeconds,
            String transcriptSummary
    ) {}

    public record AiCallExecutionListResponse(int page, int size, long total, List<AiCallExecutionResponse> rows) {}

    public record AiCallTranscriptResponse(
            UUID id,
            UUID executionId,
            String transcriptText,
            String summary,
            String sentiment,
            String outcome,
            String intent,
            boolean requiresFollowUp,
            String escalationReason,
            String extractedEntitiesJson,
            OffsetDateTime createdAt
    ) {}

    public record AiCallEventResponse(
            UUID id,
            UUID executionId,
            String providerName,
            String providerCallId,
            AiCallEventType eventType,
            String externalStatus,
            AiCallExecutionStatus internalStatus,
            OffsetDateTime eventTimestamp,
            String rawPayloadRedacted,
            OffsetDateTime createdAt
    ) {}

    public record AiCallTriggerTargetRequest(
            UUID patientId,
            UUID leadId,
            String phoneNumber,
            String script,
            OffsetDateTime scheduledAt
    ) {}

    public record AiCallTriggerRequest(List<AiCallTriggerTargetRequest> targets) {}

    public record AiCallManualCallRequest(
            UUID patientId,
            UUID leadId,
            String phoneNumber,
            UUID templateId,
            AiCallType callType,
            String script,
            OffsetDateTime scheduledAt
    ) {}

    public record AiCallActionRequest(String reason) {}

    public record AiCallRescheduleRequest(OffsetDateTime scheduledAt, String reason) {}

    public record AiCallWebhookRequest(java.util.Map<String, Object> payload) {}

    public record AiCallSchedulerHealthResponse(
            boolean enabled,
            OffsetDateTime lastRunAt,
            OffsetDateTime nextEstimatedRunAt,
            int lastProcessedCount,
            int lastDispatchedCount,
            int lastFailedCount,
            int lastSkippedCount,
            long lastDurationMs
    ) {}

    public record AiCallAnalyticsResponse(
            long totalCalls,
            long completedCalls,
            long failedCalls,
            long escalations,
            double noAnswerRate,
            double averageDurationSeconds,
            double retryRate,
            long queuedCalls,
            long suppressedCalls,
            long skippedCalls
    ) {
        public static AiCallAnalyticsResponse from(AiCallAnalyticsRecord row) {
            return new AiCallAnalyticsResponse(
                    row.totalCalls(), row.completedCalls(), row.failedCalls(), row.escalations(), row.noAnswerRate(), row.averageDurationSeconds(), row.retryRate(),
                    row.queuedCalls(), row.suppressedCalls(), row.skippedCalls()
            );
        }
    }

    public record AiCallExecutionFilter(
            AiCallExecutionStatus status,
            AiCallType callType,
            UUID patientId,
            UUID leadId,
            LocalDate startDate,
            LocalDate endDate,
            Boolean escalationRequired,
            String provider,
            UUID campaignId
    ) {}
}
