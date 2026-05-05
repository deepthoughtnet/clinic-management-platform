package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.util.Map;
import java.util.UUID;

public record AiEvidenceReference(
        AiProductCode productCode,
        UUID tenantId,
        String entityType,
        UUID entityId,
        String sourceReference,
        String textSnippet,
        Map<String, Object> metadata
) {
}
