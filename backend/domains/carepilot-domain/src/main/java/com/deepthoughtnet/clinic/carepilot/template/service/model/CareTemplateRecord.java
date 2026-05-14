package com.deepthoughtnet.clinic.carepilot.template.service.model;

import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model for centralized administration templates.
 */
public record CareTemplateRecord(
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
) {
}
