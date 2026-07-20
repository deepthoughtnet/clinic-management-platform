package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import java.math.BigDecimal;
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

    /** Request payload for campaign updates. */
    public record UpdateCampaignRequest(
            String name,
            CampaignType campaignType,
            TriggerType triggerType,
            AudienceType audienceType,
            UUID templateId,
            String notes,
            Integer expectedVersion
    ) {}

    /** Request payload for atomic edit-and-resubmit. */
    public record EditAndResubmitCampaignRequest(
            String name,
            CampaignType campaignType,
            TriggerType triggerType,
            AudienceType audienceType,
            UUID templateId,
            String notes,
            Integer expectedVersion,
            String resolutionNote
    ) {}

    /** Request payload for campaign submission or review actions. */
    public record CampaignReviewRequest(
            String comment,
            Integer expectedVersion
    ) {}

    /** Campaign lookup row for operational filters. */
    public record CampaignLookupResponse(
            UUID id,
            String campaignReference,
            String name,
            CampaignType campaignType,
            CampaignStatus status,
            UUID templateId
    ) {}

    /** Preflight summary for the manual trigger dialog. */
    public record CampaignTriggerPreviewResponse(
            String campaignReference,
            String campaignName,
            CampaignStatus status,
            TriggerType triggerType,
            String channelType,
            String templateName,
            boolean templateActive,
            String providerName,
            String providerMode,
            boolean providerReady,
            boolean manualDispatcherEnabled,
            int eligibleRecipients,
            int excludedRecipients,
            int missingEmailOrPhoneCount,
            int invalidDestinationCount,
            int consentOrOptOutCount,
            int duplicateRecipientCount,
            int inactivePatientCount,
            int missingRequiredTemplateDataCount,
            int estimatedMessages,
            BigDecimal estimatedBillableCost,
            String environmentWarning,
            boolean approvedConfigurationValid,
            boolean canTrigger,
            List<String> blockingReasons
    ) {}

    /** Audit record for campaign approval state transitions. */
    public record CampaignApprovalHistoryResponse(
            UUID id,
            UUID campaignId,
            String eventType,
            CampaignStatus fromStatus,
            CampaignStatus toStatus,
            UUID actorId,
            String actorRole,
            String actorDisplayName,
            String actorRoleLabel,
            String actorEmployeeCode,
            String actorUsername,
            String comment,
            String invalidationReason,
            String resolutionNote,
            Integer previousCampaignVersion,
            Integer campaignVersion,
            Integer newCampaignVersion,
            String previousConfigurationHash,
            String configurationHash,
            String newConfigurationHash,
            OffsetDateTime createdAt
    ) {}

    /** API response payload for campaigns. */
    public record CampaignResponse(
            UUID id,
            String campaignReference,
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
            UUID submittedBy,
            String submittedByDisplayName,
            String submittedByRoleLabel,
            String submittedByEmployeeCode,
            String submittedByUsername,
            OffsetDateTime submittedAt,
            UUID reviewedBy,
            String reviewedByDisplayName,
            String reviewedByRoleLabel,
            String reviewedByEmployeeCode,
            String reviewedByUsername,
            OffsetDateTime reviewedAt,
            String reviewComment,
            UUID approvedBy,
            String approvedByDisplayName,
            String approvedByRoleLabel,
            String approvedByEmployeeCode,
            String approvedByUsername,
            OffsetDateTime approvedAt,
            UUID activationBy,
            String activationByDisplayName,
            String activationByRoleLabel,
            String activationByEmployeeCode,
            String activationByUsername,
            OffsetDateTime activationAt,
            String approvalInvalidatedReason,
            Integer approvedVersion,
            String approvedConfigurationHash,
            int version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    /** Manual trigger result for campaign queueing actions. */
    public record CampaignTriggerResponse(
            String campaignReference,
            String executionReference,
            String campaignName,
            AudienceType audienceType,
            String channelType,
            CampaignStatus status,
            boolean queued,
            int eligibleRecipients,
            int queuedExecutions,
            int skippedRecipients,
            String message,
            OffsetDateTime queuedAt
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
            int deliveryAttemptCount,
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
