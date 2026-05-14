package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileRepository;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Minimal guardrail checks for prompt emptiness and max output token thresholds.
 */
@Service
public class AiGuardrailServiceImpl implements AiGuardrailService {
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
        AiGuardrailProfileEntity profile = resolveProfile(tenantId, profileKey);
        int limit = profile == null || profile.getMaxOutputTokens() == null
                ? defaultMaxOutputTokens
                : profile.getMaxOutputTokens();
        Integer requested = request == null ? null : request.maxTokens();
        if (requested != null && requested > limit) {
            throw new IllegalArgumentException("Requested maxTokens exceeds guardrail profile limit");
        }
    }

    @Override
    public AiGuardrailProfileEntity resolveProfile(UUID tenantId, String profileKey) {
        String key = (profileKey == null || profileKey.isBlank()) ? "default" : profileKey.trim();
        return repository.findByTenantIdAndProfileKey(tenantId, key)
                .or(() -> repository.findByTenantIdIsNullAndProfileKey(key))
                .orElse(null);
    }
}
