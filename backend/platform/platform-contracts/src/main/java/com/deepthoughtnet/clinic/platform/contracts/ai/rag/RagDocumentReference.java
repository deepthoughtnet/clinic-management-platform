package com.deepthoughtnet.clinic.platform.contracts.ai.rag;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.util.Map;
import java.util.UUID;

public record RagDocumentReference(
        AiProductCode productCode,
        UUID tenantId,
        String entityType,
        UUID entityId,
        String sourceReference,
        String textSnippet,
        Map<String, Object> metadata
) {
}
