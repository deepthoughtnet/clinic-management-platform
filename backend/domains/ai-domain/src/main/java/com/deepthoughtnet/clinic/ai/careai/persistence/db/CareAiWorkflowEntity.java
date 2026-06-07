package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowState;
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
@Table(name = "careai_workflows", indexes = {
        @Index(name = "ix_careai_workflows_tenant_conversation_state", columnList = "tenant_id,conversation_id,state"),
        @Index(name = "ix_careai_workflows_tenant_type_state", columnList = "tenant_id,workflow_type,state")
})
public class CareAiWorkflowEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "workflow_type", nullable = false, length = 64)
    private String workflowType;

    @Column(nullable = false, length = 64)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contextJson = new LinkedHashMap<>();

    @Column(name = "last_question_key", length = 128)
    private String lastQuestionKey;

    @Column(name = "repeated_question_count", nullable = false)
    private int repeatedQuestionCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public CareAiWorkflowEntity() {
    }

    public static CareAiWorkflowEntity create(
            UUID tenantId,
            UUID conversationId,
            String workflowType,
            String state,
            String contextJson,
            String lastQuestionKey,
            int repeatedQuestionCount
    ) {
        CareAiWorkflowEntity entity = new CareAiWorkflowEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.conversationId = conversationId;
        entity.workflowType = workflowType;
        entity.state = state;
        entity.contextJson = CareAiJsonSupport.parseObject(contextJson);
        entity.lastQuestionKey = lastQuestionKey;
        entity.repeatedQuestionCount = repeatedQuestionCount;
        entity.createdAt = now;
        entity.updatedAt = now;
        if (isTerminal(state)) {
            entity.completedAt = now;
        }
        return entity;
    }

    public void applySnapshot(String state, String contextJson, String lastQuestionKey, int repeatedQuestionCount) {
        this.state = state;
        this.contextJson = CareAiJsonSupport.parseObject(contextJson);
        this.lastQuestionKey = lastQuestionKey;
        this.repeatedQuestionCount = repeatedQuestionCount;
        this.updatedAt = OffsetDateTime.now();
        if (isTerminal(state)) {
            this.completedAt = this.updatedAt;
        }
    }

    private static boolean isTerminal(String state) {
        return CareAiWorkflowState.COMPLETED.name().equals(state)
                || CareAiWorkflowState.CANCELLED.name().equals(state)
                || CareAiWorkflowState.ESCALATED.name().equals(state)
                || CareAiWorkflowState.EXPIRED.name().equals(state);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getConversationId() { return conversationId; }
    public String getWorkflowType() { return workflowType; }
    public String getState() { return state; }
    public String getContextJson() { return CareAiJsonSupport.writeObject(contextJson); }
    public String getLastQuestionKey() { return lastQuestionKey; }
    public int getRepeatedQuestionCount() { return repeatedQuestionCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}
