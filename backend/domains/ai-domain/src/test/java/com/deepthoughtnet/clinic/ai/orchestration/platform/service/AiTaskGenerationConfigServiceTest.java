package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.junit.jupiter.api.Test;

class AiTaskGenerationConfigServiceTest {
    @Test
    void clinicalReasoningUsesLowThinkingStrictJsonConfig() {
        AiTaskGenerationConfigService service = new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true, 2048, 4096);

        AiTaskGenerationConfigService.GenerationConfig config = service.resolve(AiTaskType.CLINICAL_REASONING);

        assertEquals("gemini-2.5-flash", config.modelOverride());
        assertEquals(0, config.thinkingBudget());
        assertTrue(config.strictJsonMode());
        assertEquals(2048, config.maxOutputTokens());
    }

    @Test
    void clinicalReasoningOverrideBeatsDefaultGeminiModel() {
        AiTaskGenerationConfigService service = new AiTaskGenerationConfigService("gemini-2.5-flash-lite", "gemini-2.5-flash", 0, true, 2048, 4096);

        AiTaskGenerationConfigService.GenerationConfig config = service.resolve(AiTaskType.CLINICAL_REASONING);

        assertEquals("gemini-2.5-flash-lite", config.modelOverride());
    }

    @Test
    void consultationAskUsesFreeformBoundedChatConfig() {
        AiTaskGenerationConfigService service = new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true, 2048, 4096);

        AiTaskGenerationConfigService.GenerationConfig config = service.resolve(
                AiTaskType.GENERIC_COPILOT,
                "clinic.consultation.ask.v1",
                "consultation.ask"
        );

        assertEquals(1024, config.maxOutputTokens());
        assertEquals(0, config.thinkingBudget());
        assertFalse(config.strictJsonMode());
    }

    @Test
    void consultationSoapUsesExpandedOutputBudget() {
        AiTaskGenerationConfigService service = new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true, 2048, 4096);

        AiTaskGenerationConfigService.GenerationConfig config = service.resolve(AiTaskType.CONSULTATION_NOTE_STRUCTURING);

        assertEquals(4096, config.maxOutputTokens());
        assertEquals(null, config.modelOverride());
        assertEquals(null, config.thinkingBudget());
        assertTrue(config.strictJsonMode());
    }
}
