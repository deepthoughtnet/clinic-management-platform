package com.deepthoughtnet.clinic.ai.orchestration.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "agent_execution_log",
        indexes = {
                @Index(name = "ix_agent_execution_log_tenant", columnList = "tenant_id"),
                @Index(name = "ix_agent_execution_log_tenant_agent", columnList = "tenant_id,agent_type"),
                @Index(name = "ix_agent_execution_log_tenant_entity", columnList = "tenant_id,entity_id"),
                @Index(name = "ix_agent_execution_log_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_agent_execution_log_created_at", columnList = "created_at")
        }
)
public class AgentExecutionLogEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "agent_type", nullable = false)
    private String agentType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "suggestion_json", columnDefinition = "text")
    private String suggestionJson;

    @Column(nullable = false)
    private String status;

    @Column(name = "executed_by")
    private UUID executedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AgentExecutionLogEntity() {
    }

    public static AgentExecutionLogEntity create(UUID tenantId, String agentType, UUID entityId,
                                                 String suggestionJson, String status, UUID executedBy) {
        AgentExecutionLogEntity entity = new AgentExecutionLogEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.agentType = agentType;
        entity.entityId = entityId;
        entity.suggestionJson = suggestionJson;
        entity.status = status;
        entity.executedBy = executedBy;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getAgentType() {
        return agentType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getSuggestionJson() {
        return suggestionJson;
    }

    public String getStatus() {
        return status;
    }

    public UUID getExecutedBy() {
        return executedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
