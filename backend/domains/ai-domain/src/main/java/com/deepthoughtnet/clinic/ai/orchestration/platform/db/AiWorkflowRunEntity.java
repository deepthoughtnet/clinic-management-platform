package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiWorkflowRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Logged AI workflow run metadata for operational observability. */
@Entity
@Table(name = "ai_workflow_runs")
public class AiWorkflowRunEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workflow_key", nullable = false)
    private String workflowKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiWorkflowRunStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "input_summary", columnDefinition = "text")
    private String inputSummary;

    @Column(name = "output_summary", columnDefinition = "text")
    private String outputSummary;

    protected AiWorkflowRunEntity() {}

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getWorkflowKey() { return workflowKey; }
    public AiWorkflowRunStatus getStatus() { return status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public String getInputSummary() { return inputSummary; }
    public String getOutputSummary() { return outputSummary; }
}
