package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiWorkflowRepository extends JpaRepository<CareAiWorkflowEntity, UUID> {
    Optional<CareAiWorkflowEntity> findTopByTenantIdAndConversationIdOrderByUpdatedAtDesc(UUID tenantId, UUID conversationId);
}
