package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiTaskGenerationConfigService {
    private final String clinicalReasoningModelOverride;
    private final String geminiDefaultModel;
    private final Integer clinicalReasoningThinkingBudget;
    private final boolean clinicalReasoningStrictJson;

    public AiTaskGenerationConfigService(
            @Value("${clinic.ai.gemini.clinical-reasoning-model:${CLINIC_GEMINI_REASONING_MODEL:}}") String clinicalReasoningModelOverride,
            @Value("${clinic.ai.gemini.model:${CLINIC_GEMINI_MODEL:}}") String geminiDefaultModel,
            @Value("${clinic.ai.gemini.clinical-reasoning-thinking-budget:0}") Integer clinicalReasoningThinkingBudget,
            @Value("${clinic.ai.clinical-reasoning.strict-json:true}") boolean clinicalReasoningStrictJson
    ) {
        this.clinicalReasoningModelOverride = normalizeModel(clinicalReasoningModelOverride);
        this.geminiDefaultModel = normalizeModel(geminiDefaultModel);
        this.clinicalReasoningThinkingBudget = clinicalReasoningThinkingBudget == null ? 0 : Math.max(0, clinicalReasoningThinkingBudget);
        this.clinicalReasoningStrictJson = clinicalReasoningStrictJson;
    }

    public GenerationConfig resolve(AiTaskType taskType) {
        if (taskType == AiTaskType.CLINICAL_REASONING) {
            return new GenerationConfig(
                    firstNonBlank(clinicalReasoningModelOverride, geminiDefaultModel),
                    clinicalReasoningThinkingBudget,
                    clinicalReasoningStrictJson
            );
        }
        return new GenerationConfig(null, null, false);
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record GenerationConfig(String modelOverride, Integer thinkingBudget, boolean strictJsonMode) {}
}
