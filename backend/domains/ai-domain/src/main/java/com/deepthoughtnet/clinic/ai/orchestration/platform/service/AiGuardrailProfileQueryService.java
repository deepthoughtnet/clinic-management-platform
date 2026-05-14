package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only guardrail profile visibility service for AI ops. */
public interface AiGuardrailProfileQueryService {
    List<GuardrailProfileRecord> list(UUID tenantId);

    record GuardrailProfileRecord(UUID id, UUID tenantId, String profileKey, String name,
                                  String description, boolean enabled, String blockedTopicsJson,
                                  boolean piiRedactionEnabled, boolean humanApprovalRequired,
                                  Integer maxOutputTokens, OffsetDateTime updatedAt) {}
}
