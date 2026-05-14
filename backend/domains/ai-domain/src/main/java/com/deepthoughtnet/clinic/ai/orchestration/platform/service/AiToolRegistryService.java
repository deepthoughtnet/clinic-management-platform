package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Tool registry read/write foundation. */
public interface AiToolRegistryService {
    List<ToolRecord> list(UUID tenantId);

    record ToolRecord(UUID id, UUID tenantId, String toolKey, String name, String description,
                      String category, boolean enabled, String riskLevel, boolean requiresApproval,
                      String inputSchemaJson, String outputSchemaJson, OffsetDateTime updatedAt) {}
}
