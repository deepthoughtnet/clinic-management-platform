package com.deepthoughtnet.clinic.ai.orchestration.service;

import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;

public interface AiPromptTemplateRegistryService {
    AiPromptTemplateDefinition resolve(AiOrchestrationRequest request);
}
