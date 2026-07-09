package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiProviderRouterImplTest {
    @Test
    void prefersGeminiWhenAvailable() {
        AiProvider openAi = new StubProvider("OPENAI");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI");
        AiProviderRouterImpl router = new AiProviderRouterImpl(List.of(openAi, groq, gemini), "GEMINI,GROQ,MOCK");

        assertEquals("GEMINI", router.resolve(AiTaskType.SUMMARY).providerName());
        assertEquals("GEMINI", router.resolveCandidates(AiTaskType.SUMMARY).get(0).providerName());
        assertEquals("GROQ", router.resolveCandidates(AiTaskType.SUMMARY).get(1).providerName());
    }

    @Test
    void throwsWhenNoProviderAvailable() {
        AiProvider unavailable = new StubProvider("GEMINI", AiProviderStatus.UNAVAILABLE);
        AiProviderRouterImpl router = new AiProviderRouterImpl(List.of(unavailable), "GEMINI,GROQ,MOCK");

        assertThrows(IllegalStateException.class, () -> router.resolve(AiTaskType.SUMMARY));
    }

    @Test
    void genericExtractionKeepsGroqAheadOfMock() {
        AiProvider mock = new StubProvider("MOCK");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI", AiProviderStatus.UNAVAILABLE);
        AiProviderRouterImpl router = new AiProviderRouterImpl(List.of(mock, groq, gemini), "GEMINI,GROQ,MOCK");

        List<AiProvider> candidates = router.resolveCandidates(AiTaskType.GENERIC_EXTRACTION);

        assertEquals("GROQ", candidates.get(0).providerName());
        assertEquals("MOCK", candidates.get(1).providerName());
    }

    @Test
    void clinicalReasoningKeepsMockAsFinalFallback() {
        AiProvider mock = new StubProvider("MOCK");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI");
        AiProviderRouterImpl router = new AiProviderRouterImpl(List.of(mock, groq, gemini), "GEMINI,GROQ,MOCK");

        List<AiProvider> candidates = router.resolveCandidates(AiTaskType.CLINICAL_REASONING);

        assertEquals("GEMINI", candidates.get(0).providerName());
        assertEquals("GROQ", candidates.get(1).providerName());
        assertEquals("MOCK", candidates.get(2).providerName());
        assertEquals(3, candidates.size());
    }

    private static final class StubProvider implements AiProvider {
        private final String name;
        private final AiProviderStatus status;

        private StubProvider(String name) {
            this(name, AiProviderStatus.AVAILABLE);
        }

        private StubProvider(String name, AiProviderStatus status) {
            this.name = name;
            this.status = status;
        }

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public boolean supports(AiTaskType taskType) {
            return true;
        }

        @Override
        public AiProviderResponse complete(AiProviderRequest request) {
            return new AiProviderResponse(name, "model", "ok", null, BigDecimal.ONE,
                    new AiTokenUsage(1L, 1L, 2L, BigDecimal.ONE), null);
        }

        @Override
        public AiProviderStatus status() {
            return status;
        }
    }
}
