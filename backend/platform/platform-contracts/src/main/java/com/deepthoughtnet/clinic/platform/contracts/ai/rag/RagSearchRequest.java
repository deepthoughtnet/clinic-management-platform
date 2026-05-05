package com.deepthoughtnet.clinic.platform.contracts.ai.rag;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.util.Map;
import java.util.UUID;

public record RagSearchRequest(
        AiProductCode productCode,
        UUID tenantId,
        String entityType,
        UUID entityId,
        String query,
        Integer topK,
        Map<String, Object> metadata
) {
}
