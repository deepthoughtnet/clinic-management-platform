package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "careai_pending_confirmations", indexes = {
        @Index(name = "ix_careai_pending_confirmations_tenant_workflow_resolved", columnList = "tenant_id,workflow_id,resolved_at"),
        @Index(name = "ix_careai_pending_confirmations_tenant_expires", columnList = "tenant_id,expires_at")
})
public class CareAiPendingConfirmationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "confirmation_type", nullable = false, length = 64)
    private String confirmationType;

    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey;

    @Column(nullable = false)
    private int version;

    @Column(columnDefinition = "text")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJson = new LinkedHashMap<>();

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(length = 32)
    private String resolution;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CareAiPendingConfirmationEntity() {
    }

    public static CareAiPendingConfirmationEntity create(
            UUID tenantId,
            UUID workflowId,
            String confirmationType,
            String scopeKey,
            String prompt,
            String payloadJson,
            OffsetDateTime expiresAt
    ) {
        return create(tenantId, workflowId, confirmationType, scopeKey, prompt, payloadJson, expiresAt, 1);
    }

    public static CareAiPendingConfirmationEntity create(
            UUID tenantId,
            UUID workflowId,
            String confirmationType,
            String scopeKey,
            String prompt,
            String payloadJson,
            OffsetDateTime expiresAt,
            int version
    ) {
        CareAiPendingConfirmationEntity entity = new CareAiPendingConfirmationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.workflowId = workflowId;
        entity.confirmationType = confirmationType;
        entity.scopeKey = scopeKey;
        entity.version = Math.max(1, version);
        entity.prompt = prompt;
        entity.payloadJson = CareAiJsonSupport.parseObject(payloadJson);
        entity.expiresAt = expiresAt;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public void resolve(String resolution) {
        this.resolution = resolution;
        this.resolvedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkflowId() { return workflowId; }
    public String getConfirmationType() { return confirmationType; }
    public String getScopeKey() { return scopeKey; }
    public int getVersion() { return version; }
    public String getPrompt() { return prompt; }
    public String getPayloadJson() { return CareAiJsonSupport.writeObject(payloadJson); }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public String getResolution() { return resolution; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
