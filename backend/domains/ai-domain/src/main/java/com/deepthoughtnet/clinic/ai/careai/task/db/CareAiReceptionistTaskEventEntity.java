package com.deepthoughtnet.clinic.ai.careai.task.db;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiJsonSupport;
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
@Table(name = "careai_receptionist_task_events", indexes = {
        @Index(name = "ix_careai_receptionist_task_events_tenant_task_created", columnList = "tenant_id,task_id,created_at")
})
public class CareAiReceptionistTaskEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public CareAiReceptionistTaskEventEntity() {
    }

    public static CareAiReceptionistTaskEventEntity create(
            UUID tenantId,
            UUID taskId,
            String eventType,
            UUID actorUserId,
            String payloadJson
    ) {
        CareAiReceptionistTaskEventEntity entity = new CareAiReceptionistTaskEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.taskId = taskId;
        entity.eventType = eventType;
        entity.actorUserId = actorUserId;
        entity.payloadJson = CareAiJsonSupport.parseObject(payloadJson);
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTaskId() { return taskId; }
    public String getEventType() { return eventType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getPayloadJson() { return CareAiJsonSupport.writeObject(payloadJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
