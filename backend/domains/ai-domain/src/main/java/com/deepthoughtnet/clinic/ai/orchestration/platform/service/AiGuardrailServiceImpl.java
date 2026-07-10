package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileRepository;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Minimal guardrail checks for prompt emptiness and max output token thresholds.
 */
@Service
public class AiGuardrailServiceImpl implements AiGuardrailService {
    private static final Logger log = LoggerFactory.getLogger(AiGuardrailServiceImpl.class);
    private final AiGuardrailProfileRepository repository;
    private final int defaultMaxOutputTokens;

    public AiGuardrailServiceImpl(AiGuardrailProfileRepository repository,
                                  @Value("${ai.guardrails.default-max-output-tokens:2048}") int defaultMaxOutputTokens) {
        this.repository = repository;
        this.defaultMaxOutputTokens = Math.max(1, defaultMaxOutputTokens);
    }

    @Override
    public void validatePreExecution(UUID tenantId, String renderedPrompt, AiOrchestrationRequest request, String profileKey) {
        if (renderedPrompt == null || renderedPrompt.isBlank()) {
            throw new IllegalArgumentException("AI prompt content cannot be empty");
        }
    }

    @Override
    public AiGuardrailProfileEntity resolveProfile(UUID tenantId, String profileKey) {
        String key = (profileKey == null || profileKey.isBlank()) ? "default" : profileKey.trim();
        return repository.findByTenantIdAndProfileKey(tenantId, key)
                .or(() -> repository.findByTenantIdIsNullAndProfileKey(key))
                .orElse(null);
    }

    @Override
    public ExecutionSettings resolveExecutionSettings(UUID tenantId, String renderedPrompt, AiOrchestrationRequest request, String profileKey) {
        if (renderedPrompt == null || renderedPrompt.isBlank()) {
            throw new IllegalArgumentException("AI prompt content cannot be empty");
        }
        AiGuardrailProfileEntity profile = resolveProfile(tenantId, profileKey);
        int limit = profile == null || profile.getMaxOutputTokens() == null
                ? defaultMaxOutputTokens
                : Math.max(1, profile.getMaxOutputTokens());
        Integer requested = request == null ? null : request.maxTokens();
        int promptChars = renderedPrompt.length();
        int estimatedPromptTokens = Math.max(1, (promptChars + 3) / 4);
        boolean compactMode = promptChars >= Math.max(4000, limit * 2)
                || (request != null && request.useCaseCode() != null && request.useCaseCode().toLowerCase().contains("repair"));
        int effective = requested == null ? limit : Math.min(requested, limit);
        if (request != null && request.taskType() == AiTaskType.CLINICAL_REASONING) {
            int clinicalReasoningCap = Math.max(256, Math.min(limit, requested == null ? limit : requested));
            effective = Math.min(effective, clinicalReasoningCap);
        }
        if (compactMode) {
            if (request == null || request.taskType() != AiTaskType.CLINICAL_REASONING) {
                effective = Math.min(effective, Math.max(256, limit / 2));
            }
        }
        ExecutionSettings settings = new ExecutionSettings(requested, limit, effective, promptChars, estimatedPromptTokens, compactMode);
        log.debug("Resolved AI guardrail execution settings. tenantId={}, profileKey={}, requestedMaxTokens={}, guardrailLimit={}, effectiveMaxTokens={}, promptChars={}, estimatedPromptTokens={}, compactMode={}",
                tenantId,
                profileKey,
                settings.requestedMaxTokens(),
                settings.guardrailLimit(),
                settings.effectiveMaxTokens(),
                settings.promptChars(),
                settings.estimatedPromptTokens(),
                settings.compactMode());
        return settings;
    }
}
