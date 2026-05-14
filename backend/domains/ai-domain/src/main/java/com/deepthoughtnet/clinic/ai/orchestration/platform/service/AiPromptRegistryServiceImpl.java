package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiPromptDefinitionEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiPromptDefinitionRepository;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiPromptVersionEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiPromptVersionRepository;
import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiPromptVersionStatus;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-safe prompt definition/version management.
 */
@Service
public class AiPromptRegistryServiceImpl implements AiPromptRegistryService {
    private final AiPromptDefinitionRepository definitionRepository;
    private final AiPromptVersionRepository versionRepository;

    public AiPromptRegistryServiceImpl(AiPromptDefinitionRepository definitionRepository,
                                       AiPromptVersionRepository versionRepository) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptDefinitionRecord> list(UUID tenantId) {
        return definitionRepository.findByTenantIdOrTenantIdIsNullOrderByUpdatedAtDesc(tenantId)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromptDefinitionDetail get(UUID tenantId, UUID promptId) {
        AiPromptDefinitionEntity definition = requireDefinition(tenantId, promptId);
        List<PromptVersionRecord> versions = versionRepository.findByPromptDefinitionIdOrderByVersionDesc(definition.getId())
                .stream()
                .map(this::toVersionRecord)
                .toList();
        return new PromptDefinitionDetail(toRecord(definition), versions);
    }

    @Override
    @Transactional
    public PromptDefinitionRecord create(UUID tenantId, PromptUpsertCommand command, UUID actorAppUserId) {
        validateCommand(command);
        AiPromptDefinitionEntity saved = definitionRepository.save(AiPromptDefinitionEntity.create(
                tenantId,
                command.promptKey().trim(),
                command.name().trim(),
                normalize(command.description()),
                normalize(command.domain()),
                normalize(command.useCase()),
                command.systemPrompt(),
                actorAppUserId
        ));
        return toRecord(saved);
    }

    @Override
    @Transactional
    public PromptVersionRecord createVersion(UUID tenantId, UUID promptId, PromptVersionCreateCommand command, UUID actorAppUserId) {
        AiPromptDefinitionEntity definition = requireDefinition(tenantId, promptId);
        if (definition.getTenantId() == null && tenantId != null) {
            throw new IllegalArgumentException("System prompts can only be versioned by platform administrators");
        }
        int nextVersion = versionRepository.findByPromptDefinitionIdOrderByVersionDesc(definition.getId()).stream()
                .map(AiPromptVersionEntity::getVersion)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        AiPromptVersionEntity version = versionRepository.save(AiPromptVersionEntity.create(
                definition.getId(),
                nextVersion,
                normalize(command.modelHint()),
                command.temperature(),
                command.maxTokens(),
                nonBlank(command.systemPrompt(), "systemPrompt"),
                nonBlank(command.userPromptTemplate(), "userPromptTemplate"),
                normalize(command.variablesJson()),
                normalize(command.guardrailProfile())
        ));
        definition.update(definition.getName(), definition.getDescription(), definition.getDomain(), definition.getUseCase(), actorAppUserId);
        definitionRepository.save(definition);
        return toVersionRecord(version);
    }

    @Override
    @Transactional
    public PromptDefinitionDetail activateVersion(UUID tenantId, UUID promptId, UUID versionId, UUID actorAppUserId) {
        AiPromptDefinitionEntity definition = requireDefinition(tenantId, promptId);
        AiPromptVersionEntity version = versionRepository.findByIdAndPromptDefinitionId(versionId, definition.getId())
                .orElseThrow(() -> new IllegalArgumentException("Prompt version not found"));

        versionRepository.findByPromptDefinitionIdAndStatus(definition.getId(), AiPromptVersionStatus.ACTIVE)
                .ifPresent(current -> {
                    if (!current.getId().equals(version.getId())) {
                        current.archive();
                        versionRepository.save(current);
                    }
                });
        version.activate();
        versionRepository.save(version);
        definition.activateVersion(version.getVersion(), actorAppUserId);
        definitionRepository.save(definition);
        return get(tenantId, promptId);
    }

    @Override
    @Transactional
    public PromptDefinitionDetail archiveVersion(UUID tenantId, UUID promptId, UUID versionId, UUID actorAppUserId) {
        AiPromptDefinitionEntity definition = requireDefinition(tenantId, promptId);
        AiPromptVersionEntity version = versionRepository.findByIdAndPromptDefinitionId(versionId, definition.getId())
                .orElseThrow(() -> new IllegalArgumentException("Prompt version not found"));
        version.archive();
        versionRepository.save(version);
        if (definition.getActiveVersion() != null && definition.getActiveVersion() == version.getVersion()) {
            definition.clearActiveVersion(actorAppUserId);
            definitionRepository.save(definition);
        }
        return get(tenantId, promptId);
    }

    private AiPromptDefinitionEntity requireDefinition(UUID tenantId, UUID promptId) {
        AiPromptDefinitionEntity entity = definitionRepository.findById(promptId)
                .orElseThrow(() -> new IllegalArgumentException("Prompt definition not found"));
        if (entity.getTenantId() != null && !entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Prompt definition not found");
        }
        return entity;
    }

    private void validateCommand(PromptUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        nonBlank(command.promptKey(), "promptKey");
        nonBlank(command.name(), "name");
    }

    private String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PromptDefinitionRecord toRecord(AiPromptDefinitionEntity definition) {
        return new PromptDefinitionRecord(
                definition.getId(),
                definition.getTenantId(),
                definition.getPromptKey(),
                definition.getName(),
                definition.getDescription(),
                definition.getDomain(),
                definition.getUseCase(),
                definition.getActiveVersion(),
                definition.isSystemPrompt(),
                definition.getUpdatedAt()
        );
    }

    private PromptVersionRecord toVersionRecord(AiPromptVersionEntity version) {
        return new PromptVersionRecord(
                version.getId(),
                version.getVersion(),
                version.getStatus(),
                version.getModelHint(),
                version.getTemperature(),
                version.getMaxTokens(),
                version.getSystemPrompt(),
                version.getUserPromptTemplate(),
                version.getVariablesJson(),
                version.getGuardrailProfile(),
                version.getCreatedAt(),
                version.getActivatedAt()
        );
    }
}
