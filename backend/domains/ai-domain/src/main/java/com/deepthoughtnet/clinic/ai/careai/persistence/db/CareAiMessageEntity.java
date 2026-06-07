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
@Table(name = "careai_messages", indexes = {
        @Index(name = "ix_careai_messages_tenant_conversation_created", columnList = "tenant_id,conversation_id,created_at"),
        @Index(name = "ix_careai_messages_tenant_created", columnList = "tenant_id,created_at")
})
public class CareAiMessageEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 32)
    private String speaker;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 64)
    private String intent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entities_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> entitiesJson = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CareAiMessageEntity() {
    }

    public static CareAiMessageEntity create(
            UUID tenantId,
            UUID conversationId,
            String speaker,
            String channel,
            String content,
            String intent,
            String entitiesJson,
            String metadataJson
    ) {
        CareAiMessageEntity entity = new CareAiMessageEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.conversationId = conversationId;
        entity.speaker = speaker;
        entity.channel = channel;
        entity.content = content;
        entity.intent = intent;
        entity.entitiesJson = CareAiJsonSupport.parseObject(entitiesJson);
        entity.metadataJson = CareAiJsonSupport.parseObject(metadataJson);
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getConversationId() { return conversationId; }
    public String getSpeaker() { return speaker; }
    public String getChannel() { return channel; }
    public String getContent() { return content; }
    public String getIntent() { return intent; }
    public String getEntitiesJson() { return CareAiJsonSupport.writeObject(entitiesJson); }
    public String getMetadataJson() { return CareAiJsonSupport.writeObject(metadataJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
