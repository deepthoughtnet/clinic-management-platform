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
@Table(name = "careai_workflow_events", indexes = {
        @Index(name = "ix_careai_workflow_events_tenant_workflow_created", columnList = "tenant_id,workflow_id,created_at")
})
public class CareAiWorkflowEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CareAiWorkflowEventEntity() {
    }

    public static CareAiWorkflowEventEntity create(UUID tenantId, UUID workflowId, String eventType, String payloadJson) {
        CareAiWorkflowEventEntity entity = new CareAiWorkflowEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.workflowId = workflowId;
        entity.eventType = eventType;
        entity.payloadJson = CareAiJsonSupport.parseObject(payloadJson);
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkflowId() { return workflowId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return CareAiJsonSupport.writeObject(payloadJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
