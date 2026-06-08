package com.deepthoughtnet.clinic.ai.careai.task.db;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiReceptionistTaskRepository extends JpaRepository<CareAiReceptionistTaskEntity, UUID> {
    Optional<CareAiReceptionistTaskEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<CareAiReceptionistTaskEntity> findTop200ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<CareAiReceptionistTaskEntity> findTopByTenantIdAndConversationIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(
            UUID tenantId,
            UUID conversationId,
            String taskType,
            Collection<String> statuses
    );

    Optional<CareAiReceptionistTaskEntity> findTopByTenantIdAndWorkflowIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workflowId,
            String taskType,
            Collection<String> statuses
    );

    List<CareAiReceptionistTaskEntity> findByTenantIdAndStatusInOrderByCreatedAtAsc(
            UUID tenantId,
            Collection<String> statuses
    );

    long countByTenantIdAndStatusIn(UUID tenantId, Collection<String> statuses);
}
