package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiUsageAggregationService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiUsageSummaryRecord;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiUsageAggregationServiceImpl implements AiUsageAggregationService {
    private final AiRequestAuditRepository repository;

    public AiUsageAggregationServiceImpl(AiRequestAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiUsageSummaryRecord summarize(UUID tenantId, AiProductCode productCode, OffsetDateTime from, OffsetDateTime to) {
        if (tenantId == null || productCode == null || from == null || to == null) {
            return new AiUsageSummaryRecord(
                    productCode == null ? null : productCode.name(),
                    tenantId,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    java.math.BigDecimal.ZERO
            );
        }
        AiUsageSummaryRecord summary = repository.summarizeUsage(tenantId, productCode.name(), from, to);
        return summary == null ? new AiUsageSummaryRecord(productCode.name(), tenantId, 0L, 0L, 0L, 0L, 0L, 0L, 0L, java.math.BigDecimal.ZERO) : summary;
    }
}
