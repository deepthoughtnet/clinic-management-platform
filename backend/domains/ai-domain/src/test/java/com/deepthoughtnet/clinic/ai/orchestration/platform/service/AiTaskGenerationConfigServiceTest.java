package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.junit.jupiter.api.Test;

class AiTaskGenerationConfigServiceTest {
    @Test
    void clinicalReasoningUsesLowThinkingStrictJsonConfig() {
        AiTaskGenerationConfigService service = new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true);

        AiTaskGenerationConfigService.GenerationConfig config = service.resolve(AiTaskType.CLINICAL_REASONING);

        assertEquals("gemini-2.5-flash", config.modelOverride());
        assertEquals(0, config.thinkingBudget());
        assertTrue(config.strictJsonMode());
    }

    @Test
    void clinicalReasoningOverrideBeatsDefaultGeminiModel() {
        AiTaskGenerationConfigService service = new AiTaskGenerationConfigService("gemini-2.5-flash-lite", "gemini-2.5-flash", 0, true);

        AiTaskGenerationConfigService.GenerationConfig config = service.resolve(AiTaskType.CLINICAL_REASONING);

        assertEquals("gemini-2.5-flash-lite", config.modelOverride());
    }
}
