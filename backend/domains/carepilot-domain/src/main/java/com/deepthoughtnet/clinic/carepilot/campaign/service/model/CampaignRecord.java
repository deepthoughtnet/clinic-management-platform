package com.deepthoughtnet.clinic.carepilot.campaign.service.model;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for campaign API responses. */
public record CampaignRecord(
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
