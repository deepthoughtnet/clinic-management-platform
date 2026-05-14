package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGuardrailProfileRepository extends JpaRepository<AiGuardrailProfileEntity, UUID> {
    Optional<AiGuardrailProfileEntity> findByTenantIdAndProfileKey(UUID tenantId, String profileKey);

    Optional<AiGuardrailProfileEntity> findByTenantIdIsNullAndProfileKey(String profileKey);
}
