package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import java.time.OffsetDateTime;
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
}
