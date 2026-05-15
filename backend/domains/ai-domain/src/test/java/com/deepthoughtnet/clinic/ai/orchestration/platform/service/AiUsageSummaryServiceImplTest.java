package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiUsageSummaryServiceImplTest {

    @Test
    void summarizeHandlesNumericTotals() {
        AiInvocationLogRepository repository = mock(AiInvocationLogRepository.class);
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = OffsetDateTime.now();

        when(repository.countCalls(tenantId, from, to)).thenReturn(12L);
        when(repository.countSuccessful(tenantId, from, to)).thenReturn(10L);
        when(repository.countFailed(tenantId, from, to)).thenReturn(2L);
        when(repository.summarizeTotals(tenantId, from, to)).thenReturn(new Object[]{100L, 80L, BigDecimal.valueOf(4.5), 320L});
        when(repository.groupByProvider(tenantId, from, to, null, null)).thenReturn(List.<Object[]>of(new Object[]{"GROQ", 7L}));
        when(repository.groupByUseCase(tenantId, from, to, null, null)).thenReturn(List.<Object[]>of(new Object[]{"SUMMARY", 6L}));
        when(repository.groupByStatus(tenantId, from, to, null, null)).thenReturn(List.<Object[]>of(new Object[]{"SUCCESS", 10L}));

        AiUsageSummaryService.AiUsageSummary summary = new AiUsageSummaryServiceImpl(repository)
                .summarize(tenantId, from, to, null, null);

        assertEquals(12L, summary.totalCalls());
        assertEquals(100L, summary.inputTokens());
        assertEquals(80L, summary.outputTokens());
        assertEquals(BigDecimal.valueOf(4.5), summary.estimatedCost());
        assertEquals(320L, summary.avgLatencyMs());
        assertEquals(7L, summary.callsByProvider().get("GROQ"));
    }

    @Test
    void summarizeHandlesNestedObjectArrayTotals() {
        AiInvocationLogRepository repository = mock(AiInvocationLogRepository.class);
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = OffsetDateTime.now();

        when(repository.countCalls(tenantId, from, to)).thenReturn(1L);
        when(repository.countSuccessful(tenantId, from, to)).thenReturn(1L);
        when(repository.countFailed(tenantId, from, to)).thenReturn(0L);
        when(repository.summarizeTotals(tenantId, from, to))
                .thenReturn(new Object[]{new Object[]{50L}, new Object[]{25L}, BigDecimal.ONE, new Object[]{120L}});
        when(repository.groupByProvider(tenantId, from, to, null, null)).thenReturn(List.of());
        when(repository.groupByUseCase(tenantId, from, to, null, null)).thenReturn(List.of());
        when(repository.groupByStatus(tenantId, from, to, null, null)).thenReturn(List.of());

        AiUsageSummaryService.AiUsageSummary summary = new AiUsageSummaryServiceImpl(repository)
                .summarize(tenantId, from, to, null, null);

        assertEquals(50L, summary.inputTokens());
        assertEquals(25L, summary.outputTokens());
        assertEquals(120L, summary.avgLatencyMs());
    }

    @Test
    void summarizeHandlesNullTotalsAsZero() {
        AiInvocationLogRepository repository = mock(AiInvocationLogRepository.class);
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = OffsetDateTime.now();

        when(repository.countCalls(tenantId, from, to)).thenReturn(0L);
        when(repository.countSuccessful(tenantId, from, to)).thenReturn(0L);
        when(repository.countFailed(tenantId, from, to)).thenReturn(0L);
        when(repository.summarizeTotals(tenantId, from, to)).thenReturn(new Object[]{null, null, null, null});
        when(repository.groupByProvider(tenantId, from, to, null, null)).thenReturn(List.of());
        when(repository.groupByUseCase(tenantId, from, to, null, null)).thenReturn(List.of());
        when(repository.groupByStatus(tenantId, from, to, null, null)).thenReturn(List.of());

        AiUsageSummaryService.AiUsageSummary summary = new AiUsageSummaryServiceImpl(repository)
                .summarize(tenantId, from, to, null, null);

        assertEquals(0L, summary.inputTokens());
        assertEquals(0L, summary.outputTokens());
        assertEquals(BigDecimal.ZERO, summary.estimatedCost());
        assertEquals(0L, summary.avgLatencyMs());
    }

    @Test
    void summarizeThrowsClearErrorForMalformedAggregateResult() {
        AiInvocationLogRepository repository = mock(AiInvocationLogRepository.class);
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = OffsetDateTime.now();

        when(repository.countCalls(tenantId, from, to)).thenReturn(1L);
        when(repository.countSuccessful(tenantId, from, to)).thenReturn(1L);
        when(repository.countFailed(tenantId, from, to)).thenReturn(0L);
        when(repository.summarizeTotals(tenantId, from, to)).thenReturn(new Object[]{"invalid", 1L, BigDecimal.ONE, 1L});

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new AiUsageSummaryServiceImpl(repository).summarize(tenantId, from, to, null, null));
        assertTrue(ex.getMessage().contains("Expected numeric value for totals[0]"));
    }
}
