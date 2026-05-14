package com.deepthoughtnet.clinic.api.admin.dto;

import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class AdminTemplateDtos {
    private AdminTemplateDtos() {}

    public record UpsertTemplateRequest(
            String name,
            String description,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            String subject,
            String body,
            String variablesJson,
            Boolean active
    ) {}

    public record PreviewRequest(Map<String, String> variables) {}

    public record PreviewResponse(String renderedSubject, String renderedBody) {}

    public record TemplateResponse(
            UUID id,
            UUID tenantId,
            String name,
            String description,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            String subject,
            String body,
            String variablesJson,
            boolean active,
            boolean systemTemplate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID createdBy,
            UUID updatedBy
    ) {}
}
