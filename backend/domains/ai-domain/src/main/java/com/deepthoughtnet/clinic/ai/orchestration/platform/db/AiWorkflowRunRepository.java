package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiWorkflowRunRepository extends JpaRepository<AiWorkflowRunEntity, UUID> {
    List<AiWorkflowRunEntity> findTop100ByTenantIdOrderByStartedAtDesc(UUID tenantId);
}
