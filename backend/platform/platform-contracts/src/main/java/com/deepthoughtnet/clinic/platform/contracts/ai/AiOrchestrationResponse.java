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
        String errorMessage
) {
}
