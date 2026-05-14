package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiWorkflowRunStatus;
import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiWorkflowStepStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Workflow run/step visibility foundation service. */
public interface AiWorkflowLogService {
    List<WorkflowRunRecord> listRuns(UUID tenantId);

    List<WorkflowStepRecord> listSteps(UUID tenantId, UUID workflowRunId);

    record WorkflowRunRecord(UUID id, String workflowKey, AiWorkflowRunStatus status,
                             OffsetDateTime startedAt, OffsetDateTime completedAt,
                             String failureReason, String inputSummary, String outputSummary) {}

    record WorkflowStepRecord(UUID id, UUID workflowRunId, String stepName, String stepType,
                              AiWorkflowStepStatus status, OffsetDateTime startedAt,
                              OffsetDateTime completedAt, String providerName,
                              String toolKey, String errorMessage) {}
}
