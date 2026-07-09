package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GeminiAiProviderAdapterTest {
    @Test
    void mapsGeminiMaxTokensAsTruncated() {
        @SuppressWarnings("unchecked")
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        LlmClient client = mock(LlmClient.class);
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.generate(any(LlmRequest.class))).thenReturn(
                new LlmResponse("GEMINI", "gemini-2.5-flash", "Partial response",
                        new AiTokenUsage(12L, 8L, 20L, BigDecimal.valueOf(0.11)), "MAX_TOKENS")
        );

        GeminiAiProviderAdapter adapter = new GeminiAiProviderAdapter(provider);
        AiProviderResponse response = adapter.complete(new com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest(
                null, null, "system", "user", null, null, null
        ));

        assertThat(response.normalizedFinishReason()).isEqualTo("TRUNCATED");
        assertThat(response.finishReason()).isEqualTo("MAX_TOKENS");
        assertThat(response.responseChars()).isEqualTo("Partial response".length());
    }
}
