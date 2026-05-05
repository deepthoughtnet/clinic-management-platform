package com.deepthoughtnet.clinic.ai.orchestration.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AiUsageSummaryRecord(
        String productCode,
        UUID tenantId,
        Long requestCount,
        Long successCount,
        Long failedCount,
        Long fallbackCount,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        BigDecimal estimatedCost
) {
}
