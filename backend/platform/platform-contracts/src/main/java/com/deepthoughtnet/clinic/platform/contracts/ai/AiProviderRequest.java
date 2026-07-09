package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiProviderRequest(
        AiOrchestrationRequest request,
        String promptTemplateVersion,
        String systemPrompt,
        String userPrompt,
        Map<String, Object> renderedVariables,
        List<AiEvidenceReference> evidence,
        UUID requestId,
        String modelOverride,
        Integer thinkingBudget,
        boolean strictJsonMode
) {
    public AiProviderRequest(AiOrchestrationRequest request,
                             String promptTemplateVersion,
                             String systemPrompt,
                             String userPrompt,
                             Map<String, Object> renderedVariables,
                             List<AiEvidenceReference> evidence,
                             UUID requestId) {
        this(request, promptTemplateVersion, systemPrompt, userPrompt, renderedVariables, evidence, requestId, null, null, false);
    }
}
