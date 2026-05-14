package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiWorkflowStepRepository extends JpaRepository<AiWorkflowStepEntity, UUID> {
    List<AiWorkflowStepEntity> findByWorkflowRunIdOrderByStartedAtAsc(UUID workflowRunId);
}
