package com.deepthoughtnet.clinic.platform.audit.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "ix_audit_events_tenant_entity", columnList = "tenant_id,entity_type,entity_id,occurred_at"),
                @Index(name = "ix_audit_events_tenant_action", columnList = "tenant_id,action,occurred_at")
        }
)
public class AuditEventEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 96)
    private String action;

    @Column(name = "actor_app_user_id")
    private UUID actorAppUserId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditEventEntity() {
    }

    public static AuditEventEntity create(
            UUID tenantId,
            String entityType,
            UUID entityId,
            String action,
            UUID actorAppUserId,
            OffsetDateTime occurredAt,
            String summary,
            String detailsJson
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        AuditEventEntity entity = new AuditEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.entityType = entityType;
        entity.entityId = entityId;
        entity.action = action;
        entity.actorAppUserId = actorAppUserId;
        entity.occurredAt = occurredAt == null ? now : occurredAt;
        entity.summary = summary;
        entity.detailsJson = detailsJson;
        entity.createdAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorAppUserId() {
        return actorAppUserId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetailsJson() {
        return detailsJson;
    }
}
