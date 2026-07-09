package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.math.BigDecimal;

public record AiProviderResponse(
        String providerName,
        String model,
        String outputText,
        String structuredJson,
        BigDecimal confidence,
        AiTokenUsage tokenUsage,
        String finishReason,
        String normalizedFinishReason,
        Integer responseChars,
        String rawText,
        String parseStatus
) {
    public AiProviderResponse(String providerName,
                              String model,
                              String outputText,
                              String structuredJson,
                              BigDecimal confidence,
                              AiTokenUsage tokenUsage,
                              String finishReason) {
        this(providerName,
                model,
                outputText,
                structuredJson,
                confidence,
                tokenUsage,
                finishReason,
                AiFinishReasonNormalizer.normalize(finishReason),
                outputText == null ? 0 : outputText.length(),
                outputText,
                "UNKNOWN");
    }

    public AiProviderResponse {
        if (normalizedFinishReason == null || normalizedFinishReason.isBlank()) {
            normalizedFinishReason = AiFinishReasonNormalizer.normalize(finishReason);
        }
        if (responseChars == null && rawText != null) {
            responseChars = rawText.length();
        }
        if (rawText == null) {
            rawText = outputText;
        }
        if (parseStatus == null || parseStatus.isBlank()) {
            parseStatus = "UNKNOWN";
        }
    }
}
