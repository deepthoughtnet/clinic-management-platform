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

    @Test
    void excludesMockFromNonTestRuntimeChain() {
        AiProvider mock = new StubProvider("MOCK");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI");
        AiProviderRouterImpl router = new AiProviderRouterImpl(
                List.of(mock, groq, gemini), "GEMINI,GROQ,MOCK", false);

        List<AiProvider> candidates = router.resolveCandidates(AiTaskType.GENERIC_EXTRACTION);

        assertEquals(List.of("GEMINI", "GROQ"),
                candidates.stream().map(AiProvider::providerName).toList());
    }

    @Test
    void ordersSarvamAfterGroqAsFinalRealFallback() {
        AiProvider sarvam = new StubProvider("SARVAM");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI");
        AiProviderRouterImpl router = new AiProviderRouterImpl(
                List.of(sarvam, groq, gemini), "GEMINI,GROQ,SARVAM", false);

        assertEquals(List.of("GEMINI", "GROQ", "SARVAM"),
                router.resolveCandidates(AiTaskType.GENERIC_EXTRACTION).stream()
                        .map(AiProvider::providerName).toList());
    }

    @Test
    void usesSarvamFirstOnlyForAivaV2Workload() {
        AiProvider sarvam = new StubProvider("SARVAM");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI");
        AiProviderRouterImpl router = new AiProviderRouterImpl(
                List.of(groq, gemini, sarvam), "GEMINI,GROQ,SARVAM", false,
                "SARVAM,GEMINI,GROQ", "GEMINI,GROQ", null);

        assertEquals(List.of("SARVAM", "GEMINI", "GROQ"),
                router.resolveCandidates(null, AiTaskType.GENERIC_EXTRACTION,
                                "patient-portal-aiva-v2-decision").stream()
                        .map(AiProvider::providerName).toList());
    }

    @Test
    void keepsSarvamOutOfClinicalWorkload() {
        AiProvider sarvam = new StubProvider("SARVAM");
        AiProvider groq = new StubProvider("GROQ");
        AiProvider gemini = new StubProvider("GEMINI");
        AiProviderRouterImpl router = new AiProviderRouterImpl(
                List.of(sarvam, groq, gemini), "GEMINI,GROQ,SARVAM", false,
                "SARVAM,GEMINI,GROQ", "GEMINI,GROQ", null);

        assertEquals(List.of("GEMINI", "GROQ"),
                router.resolveCandidates(null, AiTaskType.CLINICAL_REASONING, "clinical-reasoning").stream()
                        .map(AiProvider::providerName).toList());
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
