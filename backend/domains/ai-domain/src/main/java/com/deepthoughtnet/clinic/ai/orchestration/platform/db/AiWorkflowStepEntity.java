package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiWorkflowStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Logged workflow step metadata for troubleshooting. */
@Entity
@Table(name = "ai_workflow_steps")
public class AiWorkflowStepEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "workflow_run_id", nullable = false)
    private UUID workflowRunId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "step_type")
    private String stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiWorkflowStepStatus status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "tool_key")
    private String toolKey;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected AiWorkflowStepEntity() {}

    public UUID getId() { return id; }
    public UUID getWorkflowRunId() { return workflowRunId; }
    public String getStepName() { return stepName; }
    public String getStepType() { return stepType; }
    public AiWorkflowStepStatus getStatus() { return status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getProviderName() { return providerName; }
    public String getToolKey() { return toolKey; }
    public String getErrorMessage() { return errorMessage; }
}
