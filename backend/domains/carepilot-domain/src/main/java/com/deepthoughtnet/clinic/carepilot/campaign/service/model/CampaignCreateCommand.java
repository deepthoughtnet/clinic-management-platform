package com.deepthoughtnet.clinic.carepilot.campaign.service.model;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import java.util.UUID;

/** Command to create a campaign definition. */
public record CampaignCreateCommand(
        String name,
        CampaignType campaignType,
        TriggerType triggerType,
        AudienceType audienceType,
        UUID templateId,
        String notes
) {}
