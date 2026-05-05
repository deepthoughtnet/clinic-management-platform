package com.deepthoughtnet.clinic.ai.orchestration.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AiRequestAuditCommand(
        UUID id,
        String productCode,
        UUID tenantId,
        UUID actorAppUserId,
        String useCaseCode,
        String taskType,
        String promptTemplateCode,
        String promptTemplateVersion,
        String provider,
        String model,
        String requestHash,
        String inputSummary,
        String outputSummary,
        String status,
        BigDecimal confidence,
        Long latencyMs,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        BigDecimal estimatedCost,
        boolean fallbackUsed,
        String errorMessage,
        String correlationId
) {
}
