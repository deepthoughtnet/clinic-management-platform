package com.deepthoughtnet.clinic.api.reliability.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_log_tenant_entity", columnList = "tenant_id,entity_type,entity_id,created_at"),
        @Index(name = "ix_audit_log_tenant_action", columnList = "tenant_id,action,created_at")
})
public class AuditLogEntity {
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

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditLogEntity() {}

    public static AuditLogEntity create(UUID tenantId, String entityType, UUID entityId, String action, UUID performedBy, String payloadJson) {
        AuditLogEntity e = new AuditLogEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.entityType = entityType;
        e.entityId = entityId;
        e.action = action;
        e.performedBy = performedBy;
        e.payloadJson = payloadJson;
        e.createdAt = OffsetDateTime.now();
        return e;
    }

    public UUID getId() {
        return id;
    }
}
