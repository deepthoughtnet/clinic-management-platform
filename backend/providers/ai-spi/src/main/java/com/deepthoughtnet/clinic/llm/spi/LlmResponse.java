package com.deepthoughtnet.clinic.llm.spi;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;

public record LlmResponse(
        String provider,
        String model,
        String text,
        AiTokenUsage tokenUsage
) {
}
