package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Aggregates usage and cost metrics from invocation metadata logs. */
@Service
public class AiUsageSummaryServiceImpl implements AiUsageSummaryService {
    private final AiInvocationLogRepository repository;

    public AiUsageSummaryServiceImpl(AiInvocationLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiUsageSummary summarize(UUID tenantId, OffsetDateTime fromDate, OffsetDateTime toDate, String provider, String useCase) {
        OffsetDateTime from = fromDate == null ? OffsetDateTime.now().minusDays(30) : fromDate;
        OffsetDateTime to = toDate == null ? OffsetDateTime.now() : toDate;

        long totalCalls = repository.countCalls(tenantId, from, to);
        long successfulCalls = repository.countSuccessful(tenantId, from, to);
        long failedCalls = repository.countFailed(tenantId, from, to);
        Object[] totals = repository.summarizeTotals(tenantId, from, to);
        long inputTokens = asLong(totals, 0);
        long outputTokens = asLong(totals, 1);
        BigDecimal estimatedCost = asDecimal(totals, 2);
        long avgLatency = asLong(totals, 3);

        return new AiUsageSummary(
                totalCalls,
                successfulCalls,
                failedCalls,
                inputTokens,
                outputTokens,
                estimatedCost,
                avgLatency,
                toMap(repository.groupByProvider(tenantId, from, to, blankToNull(provider), blankToNull(useCase))),
                toMap(repository.groupByUseCase(tenantId, from, to, blankToNull(provider), blankToNull(useCase))),
                toMap(repository.groupByStatus(tenantId, from, to, blankToNull(provider), blankToNull(useCase)))
        );
    }

    private Map<String, Long> toMap(java.util.List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(row[0] == null ? "UNKNOWN" : row[0].toString(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private long asLong(Object[] rows, int idx) {
        if (rows == null || rows.length <= idx || rows[idx] == null) {
            return 0L;
        }
        return ((Number) rows[idx]).longValue();
    }

    private BigDecimal asDecimal(Object[] rows, int idx) {
        if (rows == null || rows.length <= idx || rows[idx] == null) {
            return BigDecimal.ZERO;
        }
        if (rows[idx] instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.valueOf(((Number) rows[idx]).doubleValue());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
