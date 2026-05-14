package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Usage and cost summary service backed by invocation metadata logs. */
public interface AiUsageSummaryService {
    AiUsageSummary summarize(UUID tenantId, OffsetDateTime fromDate, OffsetDateTime toDate, String provider, String useCase);

    record AiUsageSummary(long totalCalls,
                          long successfulCalls,
                          long failedCalls,
                          long inputTokens,
                          long outputTokens,
                          BigDecimal estimatedCost,
                          long avgLatencyMs,
                          Map<String, Long> callsByProvider,
                          Map<String, Long> callsByUseCase,
                          Map<String, Long> callsByStatus) {}
}
