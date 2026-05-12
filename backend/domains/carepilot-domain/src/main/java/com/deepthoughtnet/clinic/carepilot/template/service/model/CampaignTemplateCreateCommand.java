package com.deepthoughtnet.clinic.carepilot.template.service.model;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;

/** Command to create a CarePilot content template. */
public record CampaignTemplateCreateCommand(
        String name,
        ChannelType channelType,
        String subjectLine,
        String bodyTemplate,
        boolean active
) {}
