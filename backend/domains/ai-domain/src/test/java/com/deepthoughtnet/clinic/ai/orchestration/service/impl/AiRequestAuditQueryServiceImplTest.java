package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditEntity;
import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditRepository;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRequestAuditQueryServiceImplTest {
    @Test
    void recentMapsRepositoryRows() {
        AiRequestAuditRepository repository = mock(AiRequestAuditRepository.class);
        AiRequestAuditQueryServiceImpl service = new AiRequestAuditQueryServiceImpl(repository);
        UUID tenantId = UUID.randomUUID();
        AiRequestAuditEntity entity = AiRequestAuditEntity.create(
                UUID.randomUUID(),
                AiProductCode.CLINIC.name(),
                tenantId,
                UUID.randomUUID(),
                "use-case",
                "RECONCILIATION_EXCEPTION_EXPLANATION",
                "clinic.reconciliation.exception.explain.v1",
                "v1",
                "GEMINI",
                "gemini-pro",
                "hash",
                "input summary",
                "output summary",
                "SUCCESS",
                BigDecimal.valueOf(0.88),
                123L,
                10L,
                20L,
                30L,
                BigDecimal.valueOf(0.12),
                false,
                null,
                "corr-1"
        );
        when(repository.findTop20ByTenantIdAndProductCodeOrderByCreatedAtDesc(tenantId, AiProductCode.CLINIC.name()))
                .thenReturn(List.of(entity));

        var results = service.recent(tenantId, AiProductCode.CLINIC);

        assertEquals(1, results.size());
        assertEquals("RECONCILIATION_EXCEPTION_EXPLANATION", results.getFirst().taskType());
        assertEquals("GEMINI", results.getFirst().provider());
        assertEquals("SUCCESS", results.getFirst().status());
        assertEquals("input summary", results.getFirst().inputSummary());
    }

    @Test
    void recentReturnsEmptyWhenTenantOrProductMissing() {
        AiRequestAuditRepository repository = mock(AiRequestAuditRepository.class);
        AiRequestAuditQueryServiceImpl service = new AiRequestAuditQueryServiceImpl(repository);

        assertEquals(List.of(), service.recent(null, AiProductCode.CLINIC));
        assertEquals(List.of(), service.recent(UUID.randomUUID(), null));
    }
}
