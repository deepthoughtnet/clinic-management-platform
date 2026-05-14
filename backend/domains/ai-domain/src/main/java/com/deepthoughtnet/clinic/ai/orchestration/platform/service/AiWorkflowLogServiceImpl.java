package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiWorkflowRunRepository;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiWorkflowStepRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiWorkflowLogServiceImpl implements AiWorkflowLogService {
    private final AiWorkflowRunRepository runRepository;
    private final AiWorkflowStepRepository stepRepository;

    public AiWorkflowLogServiceImpl(AiWorkflowRunRepository runRepository,
                                    AiWorkflowStepRepository stepRepository) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
    }

    @Override
    public List<WorkflowRunRecord> listRuns(UUID tenantId) {
        return runRepository.findTop100ByTenantIdOrderByStartedAtDesc(tenantId)
                .stream()
                .map(row -> new WorkflowRunRecord(
                        row.getId(),
                        row.getWorkflowKey(),
                        row.getStatus(),
                        row.getStartedAt(),
                        row.getCompletedAt(),
                        row.getFailureReason(),
                        row.getInputSummary(),
                        row.getOutputSummary()))
                .toList();
    }

    @Override
    public List<WorkflowStepRecord> listSteps(UUID tenantId, UUID workflowRunId) {
        return stepRepository.findByWorkflowRunIdOrderByStartedAtAsc(workflowRunId)
                .stream()
                .map(row -> new WorkflowStepRecord(
                        row.getId(),
                        row.getWorkflowRunId(),
                        row.getStepName(),
                        row.getStepType(),
                        row.getStatus(),
                        row.getStartedAt(),
                        row.getCompletedAt(),
                        row.getProviderName(),
                        row.getToolKey(),
                        row.getErrorMessage()))
                .toList();
    }
}
