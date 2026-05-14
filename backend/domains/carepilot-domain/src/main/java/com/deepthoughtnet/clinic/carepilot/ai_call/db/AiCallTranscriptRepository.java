package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for tenant-scoped call transcript records. */
public interface AiCallTranscriptRepository extends JpaRepository<AiCallTranscriptEntity, UUID> {
    Optional<AiCallTranscriptEntity> findByTenantIdAndExecutionId(UUID tenantId, UUID executionId);
}
