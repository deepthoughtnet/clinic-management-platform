package com.deepthoughtnet.clinic.carepilot.template.service.model;

import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;

/**
 * Filter criteria for template listings.
 */
public record CareTemplateSearchCriteria(
        TemplateType templateType,
        TemplateChannel channel,
        TemplateCategory category,
        Boolean active,
        String search
) {
}
