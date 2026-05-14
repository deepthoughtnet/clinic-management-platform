package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiPromptVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPromptVersionRepository extends JpaRepository<AiPromptVersionEntity, UUID> {
    List<AiPromptVersionEntity> findByPromptDefinitionIdOrderByVersionDesc(UUID promptDefinitionId);

    Optional<AiPromptVersionEntity> findByIdAndPromptDefinitionId(UUID id, UUID promptDefinitionId);

    Optional<AiPromptVersionEntity> findByPromptDefinitionIdAndStatus(UUID promptDefinitionId, AiPromptVersionStatus status);
}
