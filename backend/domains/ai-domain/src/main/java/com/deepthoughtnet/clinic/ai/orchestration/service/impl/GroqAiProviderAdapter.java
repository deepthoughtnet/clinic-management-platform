package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GroqAiProviderAdapter implements AiProvider {
    private static final String PROVIDER_NAME = "GROQ";

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
                || taskType == AiTaskType.GENERIC_CLASSIFICATION;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        LlmClient client = groqClientProvider == null ? null : groqClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Groq provider is not configured");
        }
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
            throw new IllegalStateException("Groq returned an empty response");
        }
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
