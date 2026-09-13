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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SarvamAiProviderAdapter implements AiProvider {
    private final ObjectProvider<LlmClient> clientProvider;
    private final int aivaMaxOutputTokens;

    public SarvamAiProviderAdapter(@Qualifier("sarvamLlmClient") ObjectProvider<LlmClient> clientProvider,
                                   @Value("${clinic.ai.sarvam.aiva-max-output-tokens:512}") int aivaMaxOutputTokens) {
        this.clientProvider = clientProvider;
        this.aivaMaxOutputTokens = Math.max(128, aivaMaxOutputTokens);
    }

    @Override public String providerName() { return "SARVAM"; }

    @Override public boolean supports(AiTaskType taskType) { return taskType != null; }

    @Override public AiProviderResponse complete(AiProviderRequest request) {
        LlmClient client = clientProvider == null ? null : clientProvider.getIfAvailable();
        if (client == null) throw new IllegalStateException("Sarvam semantic provider is not configured");
        Integer maxTokens = request.request() == null ? null : request.request().maxTokens();
        if (request.request() != null && "patient-portal-aiva-v2-decision".equalsIgnoreCase(request.request().useCaseCode())) {
            maxTokens = maxTokens == null ? aivaMaxOutputTokens : Math.min(maxTokens, aivaMaxOutputTokens);
        }
        LlmResponse response = client.generate(new LlmRequest(
                request.systemPrompt(), request.userPrompt(), null, null, null,
                request.request() == null ? null : request.request().temperature(), maxTokens,
                request.request() == null ? null : request.request().taskType(), request.modelOverride(),
                request.thinkingBudget(), request.strictJsonMode(), request.structuredOutputSchema()));
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new IllegalStateException("Sarvam returned an empty response");
        }
        String text = response.text().trim();
        return new AiProviderResponse(response.provider() == null ? providerName() : response.provider(),
                response.model(), text, null, null, response.tokenUsage(), response.finishReason(),
                response.normalizedFinishReason() == null ? AiFinishReasonNormalizer.normalize(response.finishReason()) : response.normalizedFinishReason(),
                response.responseChars() == null ? text.length() : response.responseChars(),
                response.rawText() == null ? text : response.rawText(), response.parseStatus());
    }

    @Override public AiProviderStatus status() {
        LlmClient client = clientProvider == null ? null : clientProvider.getIfAvailable();
        return client == null || !client.isAvailable() ? AiProviderStatus.UNAVAILABLE : AiProviderStatus.AVAILABLE;
    }
}
