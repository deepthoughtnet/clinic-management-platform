package com.deepthoughtnet.clinic.ai.orchestration.service;

import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiUsageSummaryRecord;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface AiUsageAggregationService {
    AiUsageSummaryRecord summarize(UUID tenantId, AiProductCode productCode, OffsetDateTime from, OffsetDateTime to);
}
