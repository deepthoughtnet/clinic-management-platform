package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GeminiAiProviderAdapter implements AiProvider {
    private final ObjectProvider<LlmClient> llmClientProvider;

    public GeminiAiProviderAdapter(@Qualifier("geminiLlmClient") ObjectProvider<LlmClient> llmClientProvider) {
        this.llmClientProvider = llmClientProvider;
    }

    @Override
    public String providerName() {
        return "GEMINI";
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType != null;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        LlmClient client = llmClientProvider == null ? null : llmClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Gemini provider is not configured");
        }
        LlmResponse response = client.generate(new LlmRequest(
                request.systemPrompt(),
                request.userPrompt(),
                null,
                null,
                null,
                request.request() == null ? null : request.request().temperature(),
                request.request() == null ? null : request.request().maxTokens(),
                request.request() == null ? null : request.request().taskType(),
                request.modelOverride(),
                request.thinkingBudget(),
                request.strictJsonMode()
        ));
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }
        String outputText = response.text().trim();
        return new AiProviderResponse(
                response.provider() == null ? providerName() : response.provider(),
                response.model(),
                outputText,
                null,
                null,
                response.tokenUsage(),
                response.finishReason(),
                response.normalizedFinishReason() == null
                        ? AiFinishReasonNormalizer.normalize(response.finishReason())
                        : response.normalizedFinishReason(),
                response.responseChars() == null ? outputText.length() : response.responseChars(),
                response.rawText() == null ? outputText : response.rawText(),
                response.parseStatus()
        );
    }

    @Override
    public AiProviderStatus status() {
        LlmClient client = llmClientProvider == null ? null : llmClientProvider.getIfAvailable();
        return client == null ? AiProviderStatus.UNAVAILABLE : AiProviderStatus.AVAILABLE;
    }
}
