package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileEntity;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import java.util.UUID;

/** Applies lightweight guardrail checks before provider execution. */
public interface AiGuardrailService {
    void validatePreExecution(UUID tenantId, String renderedPrompt, AiOrchestrationRequest request, String profileKey);

    AiGuardrailProfileEntity resolveProfile(UUID tenantId, String profileKey);
}
