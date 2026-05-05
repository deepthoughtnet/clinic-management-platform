package com.deepthoughtnet.clinic.ai.orchestration.service.model;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.List;

public record AiPromptTemplateDefinition(
        String templateCode,
        String version,
        AiProductCode productCode,
        AiTaskType taskType,
        String systemPrompt,
        String userPromptTemplate,
        AiPromptTemplateStatus status,
        String fallbackSummary,
        List<String> fallbackSuggestedActions,
        List<String> fallbackLimitations
) {
}
