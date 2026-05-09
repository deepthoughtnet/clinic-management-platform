package com.deepthoughtnet.clinic.api.ai.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AiClinicalAnalyticsResponse(
        UUID tenantId,
        OffsetDateTime from,
        OffsetDateTime to,
        Long requestCount,
        Long successCount,
        Long failedCount,
        Long fallbackCount,
        Long documentCount,
        Long reviewRequiredCount,
        Long approvedCount,
        Long rejectedCount,
        Long retryCount,
        BigDecimal averageConfidence,
        BigDecimal acceptanceRate
) {
}
