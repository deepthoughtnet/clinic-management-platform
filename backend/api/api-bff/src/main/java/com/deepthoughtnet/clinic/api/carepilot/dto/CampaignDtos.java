package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CampaignDtos {
    private CampaignDtos() {}

    /** Request payload for campaign creation. */
    public record CreateCampaignRequest(
            String name,
            CampaignType campaignType,
            TriggerType triggerType,
            AudienceType audienceType,
            UUID templateId,
            String notes
    ) {}

    /** API response payload for campaigns. */
    public record CampaignResponse(
            UUID id,
            UUID tenantId,
            String name,
            CampaignType campaignType,
            CampaignStatus status,
            TriggerType triggerType,
            AudienceType audienceType,
            UUID templateId,
            boolean active,
            String notes,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    /** Campaign runtime counters shown in campaign activity views. */
    public record CampaignRuntimeSummaryResponse(
            long totalExecutions,
            long scheduled,
            long sent,
            long failed,
            long retrying,
            long skipped,
            OffsetDateTime lastSentAt,
            OffsetDateTime lastFailedAt
    ) {}

    /** Recipient and delivery runtime row for one execution. */
    public record CampaignRuntimeExecutionResponse(
            UUID executionId,
            UUID recipientPatientId,
            String recipientPatientName,
            String recipientEmail,
            String recipientPhone,
            String relatedEntityType,
            UUID relatedEntityId,
            String relatedEntityLabel,
            String doctorName,
            String reminderWindow,
            OffsetDateTime createdAt,
            OffsetDateTime scheduledAt,
            OffsetDateTime attemptedAt,
            OffsetDateTime sentAt,
            OffsetDateTime failedAt,
            OffsetDateTime nextRetryAt,
            String channel,
            String providerName,
            String providerMessageId,
            String status,
            String failureReason,
            int retryCount
    ) {}

    /** Runtime view model for campaign details activity section. */
    public record CampaignRuntimeResponse(
            UUID campaignId,
            String campaignName,
            boolean active,
            TriggerType triggerType,
            CampaignType campaignType,
            OffsetDateTime nextExpectedExecutionAt,
            String schedulerStatus,
            OffsetDateTime lastSchedulerScanAt,
            CampaignRuntimeSummaryResponse summary,
            List<CampaignRuntimeExecutionResponse> recentExecutions
    ) {}
}
