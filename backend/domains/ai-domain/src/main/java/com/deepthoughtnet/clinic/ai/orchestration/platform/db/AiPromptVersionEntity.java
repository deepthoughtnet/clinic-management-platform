package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiPromptVersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores immutable prompt versions once activated/archived.
 */
@Entity
@Table(name = "ai_prompt_versions", indexes = {
        @Index(name = "ux_ai_prompt_versions_definition_version", columnList = "prompt_definition_id,version")
})
public class AiPromptVersionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "prompt_definition_id", nullable = false)
    private UUID promptDefinitionId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPromptVersionStatus status;

    @Column(name = "model_hint")
    private String modelHint;

    @Column(precision = 6, scale = 4)
    private BigDecimal temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "user_prompt_template", nullable = false, columnDefinition = "text")
    private String userPromptTemplate;

    @Column(name = "variables_json", columnDefinition = "text")
    private String variablesJson;

    @Column(name = "guardrail_profile")
    private String guardrailProfile;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    protected AiPromptVersionEntity() {}

    public static AiPromptVersionEntity create(UUID promptDefinitionId, int version, String modelHint,
                                               BigDecimal temperature, Integer maxTokens,
                                               String systemPrompt, String userPromptTemplate,
                                               String variablesJson, String guardrailProfile) {
        AiPromptVersionEntity entity = new AiPromptVersionEntity();
        entity.id = UUID.randomUUID();
        entity.promptDefinitionId = promptDefinitionId;
        entity.version = version;
        entity.status = AiPromptVersionStatus.DRAFT;
        entity.modelHint = modelHint;
        entity.temperature = temperature;
        entity.maxTokens = maxTokens;
        entity.systemPrompt = systemPrompt;
        entity.userPromptTemplate = userPromptTemplate;
        entity.variablesJson = variablesJson;
        entity.guardrailProfile = guardrailProfile;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public void activate() {
        this.status = AiPromptVersionStatus.ACTIVE;
        this.activatedAt = OffsetDateTime.now();
    }

    public void archive() {
        this.status = AiPromptVersionStatus.ARCHIVED;
    }

    public UUID getId() { return id; }
    public UUID getPromptDefinitionId() { return promptDefinitionId; }
    public int getVersion() { return version; }
    public AiPromptVersionStatus getStatus() { return status; }
    public String getModelHint() { return modelHint; }
    public BigDecimal getTemperature() { return temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPromptTemplate() { return userPromptTemplate; }
    public String getVariablesJson() { return variablesJson; }
    public String getGuardrailProfile() { return guardrailProfile; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
}
