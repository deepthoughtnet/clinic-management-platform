package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiToolDefinitionRepository extends JpaRepository<AiToolDefinitionEntity, UUID> {
    List<AiToolDefinitionEntity> findByTenantIdOrTenantIdIsNullOrderByUpdatedAtDesc(UUID tenantId);
}
