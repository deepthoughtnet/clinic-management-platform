package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "careai_session_bindings", indexes = {
        @Index(name = "ix_careai_session_bindings_tenant_external_session", columnList = "tenant_id,external_session_id"),
        @Index(name = "ix_careai_session_bindings_tenant_conversation_active", columnList = "tenant_id,conversation_id,active")
})
public class CareAiSessionBindingEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 64)
    private String transport;

    @Column(name = "external_session_id", nullable = false, length = 128)
    private String externalSessionId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "active_instance_id", length = 128)
    private String activeInstanceId;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CareAiSessionBindingEntity() {
    }

    public static CareAiSessionBindingEntity create(
            UUID tenantId,
            UUID conversationId,
            String transport,
            String externalSessionId,
            String activeInstanceId
    ) {
        CareAiSessionBindingEntity entity = new CareAiSessionBindingEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.conversationId = conversationId;
        entity.transport = transport;
        entity.externalSessionId = externalSessionId;
        entity.active = true;
        entity.activeInstanceId = activeInstanceId;
        entity.lastSeenAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void bindConversation(UUID conversationId) {
        this.conversationId = conversationId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSeen(String activeInstanceId, OffsetDateTime lastSeenAt) {
        this.active = true;
        this.activeInstanceId = activeInstanceId;
        this.lastSeenAt = lastSeenAt;
        this.updatedAt = lastSeenAt;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = OffsetDateTime.now();
    }
}
