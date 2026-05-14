package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPromptDefinitionRepository extends JpaRepository<AiPromptDefinitionEntity, UUID> {
    Optional<AiPromptDefinitionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AiPromptDefinitionEntity> findByTenantIdOrTenantIdIsNullOrderByUpdatedAtDesc(UUID tenantId);
}
