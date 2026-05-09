package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockAiProviderTest {
    @Test
    void returnsSafeDevelopmentResponseWithoutExternalCall() {
        MockAiProvider provider = new MockAiProvider();

        AiProviderResponse response = provider.complete(null);

        assertEquals("MOCK", response.providerName());
        assertEquals("mock-clinic-ai", response.model());
        assertEquals(new BigDecimal("0.30"), response.confidence());
        assertNotNull(response.structuredJson());
        assertTrue(response.outputText().contains("No external model was called"));
        assertTrue(response.outputText().contains("clinically reviewed"));
        assertTrue(provider.supports(AiTaskType.CLINICAL_DOCUMENT_EXTRACTION));
    }
}
