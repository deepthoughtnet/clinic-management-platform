package com.deepthoughtnet.clinic.ai.orchestration.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AiRecentRequestRecord(
        UUID auditId,
        String productCode,
        UUID tenantId,
        UUID actorAppUserId,
        String useCaseCode,
        String taskType,
        String promptTemplateCode,
        String promptTemplateVersion,
        String provider,
        String model,
        String status,
        BigDecimal confidence,
        Long latencyMs,
        boolean fallbackUsed,
        String inputSummary,
        String outputSummary,
        String correlationId,
        OffsetDateTime createdAt
) {
}
