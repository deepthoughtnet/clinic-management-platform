package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiPendingConfirmationRepository extends JpaRepository<CareAiPendingConfirmationEntity, UUID> {
    Optional<CareAiPendingConfirmationEntity> findTopByTenantIdAndWorkflowIdAndScopeKeyAndResolvedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workflowId,
            String scopeKey
    );
    Optional<CareAiPendingConfirmationEntity> findTopByTenantIdAndWorkflowIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workflowId
    );
    List<CareAiPendingConfirmationEntity> findByTenantIdAndWorkflowIdAndResolvedAtIsNull(UUID tenantId, UUID workflowId);
}
