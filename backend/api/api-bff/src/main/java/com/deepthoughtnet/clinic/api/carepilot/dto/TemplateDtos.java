package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record CreateTemplateRequest(String name, ChannelType channelType, String subjectLine, String bodyTemplate, Boolean active) {}
    public record PatchTemplateRequest(String name, String subjectLine, String bodyTemplate, Boolean active) {}
    public record TemplateResponse(
            UUID id,
            UUID tenantId,
            String name,
            ChannelType channelType,
            String subjectLine,
            String bodyTemplate,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
