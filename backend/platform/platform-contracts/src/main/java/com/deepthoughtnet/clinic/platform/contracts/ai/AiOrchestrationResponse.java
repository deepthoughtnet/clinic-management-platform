package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AiOrchestrationResponse(
        UUID requestId,
        UUID auditId,
        AiProductCode productCode,
        AiTaskType taskType,
        String provider,
        String model,
        String outputText,
        String structuredJson,
        BigDecimal confidence,
        List<AiEvidenceReference> evidence,
        List<String> suggestedActions,
        List<String> limitations,
        AiTokenUsage tokenUsage,
        Long latencyMs,
        boolean fallbackUsed,
        String errorMessage,
        String finishReason,
        String normalizedFinishReason,
        Integer responseChars,
        String rawText,
        String parseStatus
) {
    public AiOrchestrationResponse(UUID requestId,
                                   UUID auditId,
                                   AiProductCode productCode,
                                   AiTaskType taskType,
                                   String provider,
                                   String model,
                                   String outputText,
                                   String structuredJson,
                                   BigDecimal confidence,
                                   List<AiEvidenceReference> evidence,
                                   List<String> suggestedActions,
                                   List<String> limitations,
                                   AiTokenUsage tokenUsage,
                                   Long latencyMs,
                                   boolean fallbackUsed,
                                   String errorMessage,
                                   String finishReason) {
        this(requestId,
                auditId,
                productCode,
                taskType,
                provider,
                model,
                outputText,
                structuredJson,
                confidence,
                evidence,
                suggestedActions,
                limitations,
                tokenUsage,
                latencyMs,
                fallbackUsed,
                errorMessage,
                finishReason,
                AiFinishReasonNormalizer.normalize(finishReason),
                outputText == null ? 0 : outputText.length(),
                outputText,
                "UNKNOWN");
    }

    public AiOrchestrationResponse {
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
