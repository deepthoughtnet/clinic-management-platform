package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiPromptVersionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Prompt definition and version management service. */
public interface AiPromptRegistryService {
    List<PromptDefinitionRecord> list(UUID tenantId);

    PromptDefinitionDetail get(UUID tenantId, UUID promptId);

    PromptDefinitionRecord create(UUID tenantId, PromptUpsertCommand command, UUID actorAppUserId);

    PromptVersionRecord createVersion(UUID tenantId, UUID promptId, PromptVersionCreateCommand command, UUID actorAppUserId);

    PromptDefinitionDetail activateVersion(UUID tenantId, UUID promptId, UUID versionId, UUID actorAppUserId);

    PromptDefinitionDetail archiveVersion(UUID tenantId, UUID promptId, UUID versionId, UUID actorAppUserId);

    record PromptUpsertCommand(String promptKey, String name, String description, String domain,
                               String useCase, boolean systemPrompt) {}

    record PromptVersionCreateCommand(String modelHint, BigDecimal temperature, Integer maxTokens,
                                      String systemPrompt, String userPromptTemplate,
                                      String variablesJson, String guardrailProfile) {}

    record PromptDefinitionRecord(UUID id, UUID tenantId, String promptKey, String name, String description,
                                  String domain, String useCase, Integer activeVersion,
                                  boolean systemPrompt, OffsetDateTime updatedAt) {}

    record PromptVersionRecord(UUID id, int version, AiPromptVersionStatus status, String modelHint,
                               BigDecimal temperature, Integer maxTokens, String systemPrompt,
                               String userPromptTemplate, String variablesJson, String guardrailProfile,
                               OffsetDateTime createdAt, OffsetDateTime activatedAt) {}

    record PromptDefinitionDetail(PromptDefinitionRecord definition, List<PromptVersionRecord> versions) {}
}
