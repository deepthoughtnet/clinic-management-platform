package com.deepthoughtnet.clinic.api.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiDraftResponse(
        boolean enabled,
        boolean fallbackUsed,
        String message,
        String provider,
        String model,
        String draft,
        Map<String, Object> structuredData,
        BigDecimal confidence,
        List<String> suggestedActions,
        List<String> warnings,
        String finishReason,
        String normalizedFinishReason,
        Integer responseChars,
        String rawText,
        String parseStatus
) {
    public AiDraftResponse(boolean enabled,
                           boolean fallbackUsed,
                           String message,
                           String provider,
                           String model,
                           String draft,
                           Map<String, Object> structuredData,
                           BigDecimal confidence,
                           List<String> suggestedActions,
                           List<String> warnings,
                           String finishReason) {
        this(enabled,
                fallbackUsed,
                message,
                provider,
                model,
                draft,
                structuredData,
                confidence,
                suggestedActions,
                warnings,
                finishReason,
                com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer.normalize(finishReason),
                draft == null ? 0 : draft.length(),
                draft,
                "UNKNOWN");
    }

    public AiDraftResponse {
        if (normalizedFinishReason == null || normalizedFinishReason.isBlank()) {
            normalizedFinishReason = com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer.normalize(finishReason);
        }
        if (responseChars == null && rawText != null) {
            responseChars = rawText.length();
        }
        if (rawText == null) {
            rawText = draft;
        }
        if (parseStatus == null || parseStatus.isBlank()) {
            parseStatus = "UNKNOWN";
        }
    }
}
