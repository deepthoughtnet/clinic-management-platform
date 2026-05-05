package com.deepthoughtnet.clinic.ai.orchestration.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionLogRepository extends JpaRepository<AgentExecutionLogEntity, UUID> {
    List<AgentExecutionLogEntity> findTop20ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
