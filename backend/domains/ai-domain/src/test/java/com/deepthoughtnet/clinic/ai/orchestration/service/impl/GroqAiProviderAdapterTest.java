package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GroqAiProviderAdapterTest {
    @Test
    void mapsGroqTokenUsageIntoAiResponse() {
        @SuppressWarnings("unchecked")
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        LlmClient client = mock(LlmClient.class);
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.generate(org.mockito.ArgumentMatchers.any(LlmRequest.class))).thenReturn(
                new LlmResponse("GROQ", "llama", "Structured response",
                        new AiTokenUsage(11L, 7L, 18L, BigDecimal.valueOf(0.09)))
        );

        GroqAiProviderAdapter adapter = new GroqAiProviderAdapter(provider);
        AiProviderResponse response = adapter.complete(new com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest(
                null, null, "system", "user", null, null, null
        ));

        assertEquals("GROQ", response.providerName());
        assertEquals("llama", response.model());
        assertEquals("Structured response", response.outputText());
        assertEquals(11L, response.tokenUsage().promptTokens());
        assertEquals(7L, response.tokenUsage().completionTokens());
        assertEquals(18L, response.tokenUsage().totalTokens());
        assertEquals(BigDecimal.valueOf(0.09), response.tokenUsage().estimatedCost());
    }

    @Test
    void returnsUnavailableWhenGroqClientMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        GroqAiProviderAdapter adapter = new GroqAiProviderAdapter(provider);

        assertEquals(AiProviderStatus.UNAVAILABLE, adapter.status());
        assertNull(provider.getIfAvailable());
    }

    @Test
    void supportsOnlyTextCopilotTasks() {
        GroqAiProviderAdapter adapter = new GroqAiProviderAdapter(mock(ObjectProvider.class));
        assertEquals(true, adapter.supports(AiTaskType.SUMMARY));
        assertEquals(false, adapter.supports(AiTaskType.CLINIC_EXTRACTION));
    }
}
