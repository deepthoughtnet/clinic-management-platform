package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiTaskGenerationConfigService {
    private static final String CONSULTATION_ASK_TEMPLATE_CODE = "clinic.consultation.ask.v1";
    private static final String CONSULTATION_ASK_USE_CASE = "consultation.ask";
    private final String clinicalReasoningModelOverride;
    private final String geminiDefaultModel;
    private final Integer clinicalReasoningThinkingBudget;
    private final boolean clinicalReasoningStrictJson;
    private final Integer clinicalReasoningMaxOutputTokens;

    public AiTaskGenerationConfigService(
            @Value("${clinic.ai.gemini.clinical-reasoning-model:${CLINIC_GEMINI_REASONING_MODEL:}}") String clinicalReasoningModelOverride,
            @Value("${clinic.ai.gemini.model:${CLINIC_GEMINI_MODEL:}}") String geminiDefaultModel,
            @Value("${clinic.ai.gemini.clinical-reasoning-thinking-budget:0}") Integer clinicalReasoningThinkingBudget,
            @Value("${clinic.ai.clinical-reasoning.strict-json:true}") boolean clinicalReasoningStrictJson,
            @Value("${clinic.ai.clinical-reasoning.max-output-tokens:2048}") Integer clinicalReasoningMaxOutputTokens
    ) {
        this.clinicalReasoningModelOverride = normalizeModel(clinicalReasoningModelOverride);
        this.geminiDefaultModel = normalizeModel(geminiDefaultModel);
        this.clinicalReasoningThinkingBudget = clinicalReasoningThinkingBudget == null ? 0 : Math.max(0, clinicalReasoningThinkingBudget);
        this.clinicalReasoningStrictJson = clinicalReasoningStrictJson;
        this.clinicalReasoningMaxOutputTokens = clinicalReasoningMaxOutputTokens == null ? null : Math.max(256, clinicalReasoningMaxOutputTokens);
    }

    public GenerationConfig resolve(AiTaskType taskType) {
        return resolve(taskType, null, null);
    }

    public GenerationConfig resolve(AiTaskType taskType, String templateCode, String useCaseCode) {
        if (isConsultationAsk(templateCode, useCaseCode)) {
            return new GenerationConfig(
                    null,
                    0,
                    false,
                    1024
            );
        }
        if (taskType == AiTaskType.CLINICAL_REASONING) {
            return new GenerationConfig(
                    firstNonBlank(clinicalReasoningModelOverride, geminiDefaultModel),
                    clinicalReasoningThinkingBudget,
                    clinicalReasoningStrictJson,
                    clinicalReasoningMaxOutputTokens
            );
        }
        return new GenerationConfig(null, null, false, null);
    }

    private boolean isConsultationAsk(String templateCode, String useCaseCode) {
        return CONSULTATION_ASK_TEMPLATE_CODE.equalsIgnoreCase(normalize(templateCode))
                || CONSULTATION_ASK_USE_CASE.equalsIgnoreCase(normalize(useCaseCode));
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    public record GenerationConfig(String modelOverride, Integer thinkingBudget, boolean strictJsonMode, Integer maxOutputTokens) {}
}
