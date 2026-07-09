package com.deepthoughtnet.clinic.llm.spi;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer;

public record LlmResponse(
        String provider,
        String model,
        String text,
        AiTokenUsage tokenUsage,
        String finishReason,
        String normalizedFinishReason,
        Integer responseChars,
        String rawText,
        String parseStatus
) {
    public LlmResponse(String provider, String model, String text, AiTokenUsage tokenUsage, String finishReason) {
        this(provider,
                model,
                text,
                tokenUsage,
                finishReason,
                AiFinishReasonNormalizer.normalize(finishReason),
                text == null ? 0 : text.length(),
                text,
                "UNKNOWN");
    }

    public LlmResponse {
        if (normalizedFinishReason == null || normalizedFinishReason.isBlank()) {
            normalizedFinishReason = AiFinishReasonNormalizer.normalize(finishReason);
        }
        if (responseChars == null && rawText != null) {
            responseChars = rawText.length();
        }
        if (rawText == null) {
            rawText = text;
        }
        if (parseStatus == null || parseStatus.isBlank()) {
            parseStatus = "UNKNOWN";
        }
    }
}
