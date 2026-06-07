package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiMessageRepository extends JpaRepository<CareAiMessageEntity, UUID> {
    List<CareAiMessageEntity> findByTenantIdAndConversationIdOrderByCreatedAtAsc(UUID tenantId, UUID conversationId);
}
