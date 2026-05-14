package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Guardrail profile used to enforce basic safety and output limits. */
@Entity
@Table(name = "ai_guardrail_profiles")
public class AiGuardrailProfileEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "profile_key", nullable = false)
    private String profileKey;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "blocked_topics_json", columnDefinition = "text")
    private String blockedTopicsJson;

    @Column(name = "pii_redaction_enabled", nullable = false)
    private boolean piiRedactionEnabled;

    @Column(name = "human_approval_required", nullable = false)
    private boolean humanApprovalRequired;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiGuardrailProfileEntity() {}

    public static AiGuardrailProfileEntity create(UUID tenantId, String profileKey, String name, String description,
                                                  boolean enabled, String blockedTopicsJson,
                                                  boolean piiRedactionEnabled, boolean humanApprovalRequired,
                                                  Integer maxOutputTokens) {
        OffsetDateTime now = OffsetDateTime.now();
        AiGuardrailProfileEntity entity = new AiGuardrailProfileEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.profileKey = profileKey;
        entity.name = name;
        entity.description = description;
        entity.enabled = enabled;
        entity.blockedTopicsJson = blockedTopicsJson;
        entity.piiRedactionEnabled = piiRedactionEnabled;
        entity.humanApprovalRequired = humanApprovalRequired;
        entity.maxOutputTokens = maxOutputTokens;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProfileKey() { return profileKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public String getBlockedTopicsJson() { return blockedTopicsJson; }
    public boolean isPiiRedactionEnabled() { return piiRedactionEnabled; }
    public boolean isHumanApprovalRequired() { return humanApprovalRequired; }
    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
