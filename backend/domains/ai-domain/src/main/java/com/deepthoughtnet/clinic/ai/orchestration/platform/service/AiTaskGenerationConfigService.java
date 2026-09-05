package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiTaskGenerationConfigService {
    private static final String CONSULTATION_ASK_TEMPLATE_CODE = "clinic.consultation.ask.v1";
    private static final String CONSULTATION_ASK_USE_CASE = "consultation.ask";
    private static final String CONSULTATION_EXPLAIN_DIAGNOSIS_TEMPLATE_CODE = "clinic.consultation.explain-diagnosis.v1";
    private static final String CONSULTATION_EXPLAIN_DIAGNOSIS_USE_CASE = "consultation.explain-diagnosis";
    private static final String CONSULTATION_HISTORY_GAP_TEMPLATE_CODE = "clinic.consultation.history-gaps.v1";
    private static final String CONSULTATION_HISTORY_GAP_USE_CASE = "consultation.history-gaps";
    private static final String CONSULTATION_SUGGEST_TESTS_TEMPLATE_CODE = "clinic.consultation.suggest-tests.v1";
    private static final String CONSULTATION_SUGGEST_TESTS_USE_CASE = "consultation.suggest-tests";
    private final String clinicalReasoningModelOverride;
    private final String geminiDefaultModel;
    private final Integer clinicalReasoningThinkingBudget;
    private final boolean clinicalReasoningStrictJson;
    private final Integer clinicalReasoningMaxOutputTokens;
    private final Integer clinicalDocumentExtractionThinkingBudget;
    private final Integer clinicalDocumentExtractionMaxOutputTokens;
    private final Integer consultationSoapMaxOutputTokens;

    @Autowired
    public AiTaskGenerationConfigService(
            @Value("${clinic.ai.gemini.clinical-reasoning-model:${CLINIC_GEMINI_REASONING_MODEL:}}") String clinicalReasoningModelOverride,
            @Value("${clinic.ai.gemini.model:${CLINIC_GEMINI_MODEL:}}") String geminiDefaultModel,
            @Value("${clinic.ai.gemini.clinical-reasoning-thinking-budget:0}") Integer clinicalReasoningThinkingBudget,
            @Value("${clinic.ai.clinical-reasoning.strict-json:true}") boolean clinicalReasoningStrictJson,
            @Value("${clinic.ai.clinical-reasoning.max-output-tokens:2048}") Integer clinicalReasoningMaxOutputTokens,
            @Value("${clinic.ai.gemini.clinical-document-extraction-thinking-budget:0}") Integer clinicalDocumentExtractionThinkingBudget,
            @Value("${clinic.ai.clinical-document-extraction.max-output-tokens:4096}") Integer clinicalDocumentExtractionMaxOutputTokens,
            @Value("${clinic.ai.soap-note.max-output-tokens:${CLINIC_SOAP_NOTE_MAX_OUTPUT_TOKENS:4096}}") Integer consultationSoapMaxOutputTokens
    ) {
        this.clinicalReasoningModelOverride = normalizeModel(clinicalReasoningModelOverride);
        this.geminiDefaultModel = normalizeModel(geminiDefaultModel);
        this.clinicalReasoningThinkingBudget = clinicalReasoningThinkingBudget == null ? 0 : Math.max(0, clinicalReasoningThinkingBudget);
        this.clinicalReasoningStrictJson = clinicalReasoningStrictJson;
        this.clinicalReasoningMaxOutputTokens = clinicalReasoningMaxOutputTokens == null ? null : Math.max(256, clinicalReasoningMaxOutputTokens);
        this.clinicalDocumentExtractionThinkingBudget = clinicalDocumentExtractionThinkingBudget == null ? 0 : Math.max(0, clinicalDocumentExtractionThinkingBudget);
        this.clinicalDocumentExtractionMaxOutputTokens = clinicalDocumentExtractionMaxOutputTokens == null ? 4096 : Math.max(1024, clinicalDocumentExtractionMaxOutputTokens);
        this.consultationSoapMaxOutputTokens = consultationSoapMaxOutputTokens == null ? 4096 : Math.max(512, consultationSoapMaxOutputTokens);
    }

    public AiTaskGenerationConfigService(
            String clinicalReasoningModelOverride,
            String geminiDefaultModel,
            Integer clinicalReasoningThinkingBudget,
            boolean clinicalReasoningStrictJson,
            Integer clinicalReasoningMaxOutputTokens,
            Integer consultationSoapMaxOutputTokens
    ) {
        this(clinicalReasoningModelOverride, geminiDefaultModel, clinicalReasoningThinkingBudget,
                clinicalReasoningStrictJson, clinicalReasoningMaxOutputTokens, 0, 4096,
                consultationSoapMaxOutputTokens);
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
        if (taskType == AiTaskType.CLINICAL_DOCUMENT_EXTRACTION) {
            return new GenerationConfig(
                    null,
                    clinicalDocumentExtractionThinkingBudget,
                    true,
                    clinicalDocumentExtractionMaxOutputTokens
            );
        }
        if (taskType == AiTaskType.CONSULTATION_NOTE_STRUCTURING) {
            return new GenerationConfig(
                    null,
                    null,
                    true,
                    consultationSoapMaxOutputTokens
            );
        }
        return new GenerationConfig(null, null, false, null);
    }

    private boolean isConsultationAsk(String templateCode, String useCaseCode) {
        return CONSULTATION_ASK_TEMPLATE_CODE.equalsIgnoreCase(normalize(templateCode))
                || CONSULTATION_ASK_USE_CASE.equalsIgnoreCase(normalize(useCaseCode))
                || CONSULTATION_EXPLAIN_DIAGNOSIS_TEMPLATE_CODE.equalsIgnoreCase(normalize(templateCode))
                || CONSULTATION_EXPLAIN_DIAGNOSIS_USE_CASE.equalsIgnoreCase(normalize(useCaseCode))
                || CONSULTATION_HISTORY_GAP_TEMPLATE_CODE.equalsIgnoreCase(normalize(templateCode))
                || CONSULTATION_HISTORY_GAP_USE_CASE.equalsIgnoreCase(normalize(useCaseCode))
                || CONSULTATION_SUGGEST_TESTS_TEMPLATE_CODE.equalsIgnoreCase(normalize(templateCode))
                || CONSULTATION_SUGGEST_TESTS_USE_CASE.equalsIgnoreCase(normalize(useCaseCode));
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
