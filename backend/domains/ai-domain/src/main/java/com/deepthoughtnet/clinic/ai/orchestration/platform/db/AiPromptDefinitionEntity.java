package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores tenant/global prompt definitions and currently active prompt version references.
 */
@Entity
@Table(name = "ai_prompt_definitions", indexes = {
        @Index(name = "ix_ai_prompt_definitions_tenant_domain", columnList = "tenant_id,domain"),
        @Index(name = "ix_ai_prompt_definitions_tenant_use_case", columnList = "tenant_id,use_case")
})
public class AiPromptDefinitionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "prompt_key", nullable = false)
    private String promptKey;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column
    private String domain;

    @Column(name = "use_case")
    private String useCase;

    @Column(name = "active_version")
    private Integer activeVersion;

    @Column(name = "is_system_prompt", nullable = false)
    private boolean systemPrompt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected AiPromptDefinitionEntity() {}

    public static AiPromptDefinitionEntity create(UUID tenantId, String promptKey, String name, String description,
                                                  String domain, String useCase, boolean systemPrompt, UUID actor) {
        OffsetDateTime now = OffsetDateTime.now();
        AiPromptDefinitionEntity entity = new AiPromptDefinitionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.promptKey = promptKey;
        entity.name = name;
        entity.description = description;
        entity.domain = domain;
        entity.useCase = useCase;
        entity.systemPrompt = systemPrompt;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.createdBy = actor;
        entity.updatedBy = actor;
        return entity;
    }

    public void update(String name, String description, String domain, String useCase, UUID actor) {
        this.name = name;
        this.description = description;
        this.domain = domain;
        this.useCase = useCase;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actor;
    }

    public void activateVersion(int version, UUID actor) {
        this.activeVersion = version;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actor;
    }

    public void clearActiveVersion(UUID actor) {
        this.activeVersion = null;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPromptKey() { return promptKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDomain() { return domain; }
    public String getUseCase() { return useCase; }
    public Integer getActiveVersion() { return activeVersion; }
    public boolean isSystemPrompt() { return systemPrompt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
}
