package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for CarePilot reminders management operational APIs.
 */
public final class RemindersDtos {
    private RemindersDtos() {}

    /** Reminder execution row displayed in reminders operational grid. */
    public record ReminderRowResponse(
            String executionId,
            String campaignId,
            String campaignName,
            CampaignType campaignType,
            TriggerType triggerType,
            String patientId,
            String patientName,
            String patientEmail,
            String patientPhone,
            ChannelType channel,
            String providerName,
            String providerMessageId,
            ExecutionStatus executionStatus,
            MessageDeliveryStatus deliveryStatus,
            OffsetDateTime scheduledAt,
            OffsetDateTime attemptedAt,
            OffsetDateTime sentAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime readAt,
            OffsetDateTime failedAt,
            OffsetDateTime nextRetryAt,
            int retryCount,
            String failureReason,
            String relatedEntityType,
            String relatedEntityId,
            String relatedEntityLabel,
            String reminderReason,
            OffsetDateTime createdAt
    ) {}

    /** Paginated reminders list payload for management UI. */
    public record ReminderListResponse(
            int page,
            int size,
            long total,
            List<ReminderRowResponse> rows
    ) {}

    /** Reminder detail combines row and existing timeline projection. */
    public record ReminderDetailResponse(
            ReminderRowResponse reminder,
            AnalyticsDtos.ExecutionTimelineResponse timeline
    ) {}

    /** Mutation request used by cancel/suppress/reschedule actions. */
    public record ReminderMutationRequest(
            String reason,
            OffsetDateTime newScheduledAt
    ) {}
}
