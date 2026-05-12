package com.deepthoughtnet.clinic.carepilot.template.service.model;

/** Partial update command for template maintenance. */
public record CampaignTemplatePatchCommand(
        String name,
        String subjectLine,
        String bodyTemplate,
        Boolean active
) {}
