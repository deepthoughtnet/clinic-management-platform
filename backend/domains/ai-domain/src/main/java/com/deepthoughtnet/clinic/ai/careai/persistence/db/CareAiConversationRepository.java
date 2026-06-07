package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiConversationRepository extends JpaRepository<CareAiConversationEntity, UUID> {
    List<CareAiConversationEntity> findTop200ByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    Optional<CareAiConversationEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<CareAiConversationEntity> findTopByTenantIdAndChannelAndExternalSessionIdAndStatusInOrderByUpdatedAtDesc(
            UUID tenantId,
            String channel,
            String externalSessionId,
            List<String> statuses
    );
    Optional<CareAiConversationEntity> findTopByTenantIdAndChannelAndPatientIdAndStatusInOrderByUpdatedAtDesc(
            UUID tenantId,
            String channel,
            UUID patientId,
            List<String> statuses
    );
}
