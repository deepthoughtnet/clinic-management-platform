package com.deepthoughtnet.clinic.carepilot.template.service.model;

import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;

/**
 * Create or update command for administration templates.
 */
public record CareTemplateUpsertCommand(
        String name,
        String description,
        TemplateType templateType,
        TemplateChannel channel,
        TemplateCategory category,
        String subject,
        String body,
        String variablesJson,
        Boolean active
) {
}
