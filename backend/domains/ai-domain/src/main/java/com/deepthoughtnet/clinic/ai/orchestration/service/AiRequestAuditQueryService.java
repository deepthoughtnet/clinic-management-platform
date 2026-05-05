package com.deepthoughtnet.clinic.ai.orchestration.service;

import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRecentRequestRecord;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.util.List;
import java.util.UUID;

public interface AiRequestAuditQueryService {
    List<AiRecentRequestRecord> recent(UUID tenantId, AiProductCode productCode);
}
