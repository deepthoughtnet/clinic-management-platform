package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for append-only AI call event history. */
public interface AiCallEventRepository extends JpaRepository<AiCallEventEntity, UUID> {
    List<AiCallEventEntity> findTop100ByTenantIdAndExecutionIdOrderByCreatedAtDesc(UUID tenantId, UUID executionId);
    Optional<AiCallEventEntity> findTopByTenantIdAndProviderCallIdOrderByCreatedAtDesc(UUID tenantId, String providerCallId);
    long countByTenantIdAndCreatedAtGreaterThanEqual(UUID tenantId, OffsetDateTime createdAt);
}
