package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.util.Map;

public record ReasoningMetadata(
        String reasoningEngineVersion,
        String promptVersion,
        String contextVersion,
        String schemaVersion,
        String provider,
        String model,
        Map<String, Object> tokens,
        String parseStatus,
        String requestId,
        String correlationId,
        Long latencyMs,
        boolean fallbackUsed,
        boolean retryUsed,
        String finishReason,
        String normalizedFinishReason,
        Integer responseChars,
        String rawText,
        Integer rawChars,
        String errorMessage,
        String resultQuality
) {
    public ReasoningMetadata(String reasoningEngineVersion,
                             String promptVersion,
                             String contextVersion,
                             String schemaVersion,
                             String provider,
                             String model,
                             Map<String, Object> tokens,
                             String parseStatus,
                             String requestId,
                             String correlationId,
                             Long latencyMs,
                             boolean fallbackUsed,
                             boolean retryUsed,
                             String finishReason,
                             Integer rawChars,
                             String errorMessage,
                             String resultQuality) {
        this(reasoningEngineVersion,
                promptVersion,
                contextVersion,
                schemaVersion,
                provider,
                model,
                tokens,
                parseStatus,
                requestId,
                correlationId,
                latencyMs,
                fallbackUsed,
                retryUsed,
                finishReason,
                com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer.normalize(finishReason),
                rawChars,
                null,
                rawChars,
                errorMessage,
                resultQuality);
    }

    public ReasoningMetadata {
        if (normalizedFinishReason == null || normalizedFinishReason.isBlank()) {
            normalizedFinishReason = com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer.normalize(finishReason);
        }
        if (responseChars == null && rawText != null) {
            responseChars = rawText.length();
        }
        if (rawText == null && rawChars != null) {
            responseChars = responseChars == null ? rawChars : responseChars;
        }
    }
}
