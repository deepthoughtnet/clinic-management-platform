package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GroqAiProviderAdapter implements AiProvider {
    private static final String PROVIDER_NAME = "GROQ";
    private static final Logger log = LoggerFactory.getLogger(GroqAiProviderAdapter.class);

    private final ObjectProvider<LlmClient> groqClientProvider;

    public GroqAiProviderAdapter(@Qualifier("groqLlmClient") ObjectProvider<LlmClient> groqClientProvider) {
        this.groqClientProvider = groqClientProvider;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType == AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION
                || taskType == AiTaskType.RECONCILIATION_MATCH_EXPLANATION
                || taskType == AiTaskType.SUMMARY
                || taskType == AiTaskType.GENERIC_COPILOT
                || taskType == AiTaskType.CLINIC_RISK_EXPLANATION
                || taskType == AiTaskType.DUPLICATE_EXPLANATION
                || taskType == AiTaskType.DOCTOR_RESUBMISSION_SUGGESTION
                || taskType == AiTaskType.GENERIC_RECOMMENDATION
                || taskType == AiTaskType.GENERIC_CLASSIFICATION
                || taskType == AiTaskType.PATIENT_HISTORY_SUMMARY
                || taskType == AiTaskType.CONSULTATION_NOTE_STRUCTURING
                || taskType == AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT
                || taskType == AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION
                || taskType == AiTaskType.PATIENT_INSTRUCTIONS_DRAFT
                || taskType == AiTaskType.ALLERGY_CONDITION_WARNING;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        long started = System.currentTimeMillis();
        LlmClient client = groqClientProvider == null ? null : groqClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("Groq provider unavailable. requestId={}", request == null ? null : request.requestId());
            throw new IllegalStateException("Groq provider is not configured");
        }
        log.info("Calling Groq provider. requestId={}, chars={}, hasAttachment={}",
                request == null ? null : request.requestId(),
                request == null || request.userPrompt() == null ? 0 : request.userPrompt().length(),
                false);
        LlmResponse response = client.generate(new LlmRequest(
                request.systemPrompt(),
                request.userPrompt(),
                null,
                null,
                null,
                request.request() == null ? null : request.request().temperature(),
                request.request() == null ? null : request.request().maxTokens()
        ));
        if (response == null || response.text() == null || response.text().isBlank()) {
            log.warn("LLM provider returned empty content. provider=GROQ, requestId={}", request == null ? null : request.requestId());
            throw new IllegalStateException("Groq returned an empty response");
        }
        log.info("Groq response received. requestId={}, latencyMs={}, responseChars={}, tokenUsage={}",
                request == null ? null : request.requestId(),
                System.currentTimeMillis() - started,
                response.text().trim().length(),
                response.tokenUsage() == null ? "n/a" : ("prompt=" + response.tokenUsage().promptTokens()
                        + ",completion=" + response.tokenUsage().completionTokens()
                        + ",total=" + response.tokenUsage().totalTokens()));
        return new AiProviderResponse(
                response.provider() == null ? providerName() : response.provider(),
                response.model(),
                response.text().trim(),
                null,
                null,
                response.tokenUsage()
        );
    }

    @Override
    public AiProviderStatus status() {
        LlmClient client = groqClientProvider == null ? null : groqClientProvider.getIfAvailable();
        return client == null ? AiProviderStatus.UNAVAILABLE : AiProviderStatus.AVAILABLE;
    }
}
