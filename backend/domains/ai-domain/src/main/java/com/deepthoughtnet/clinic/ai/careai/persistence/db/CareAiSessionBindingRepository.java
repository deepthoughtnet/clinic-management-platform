package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiSessionBindingRepository extends JpaRepository<CareAiSessionBindingEntity, UUID> {
    Optional<CareAiSessionBindingEntity> findByTenantIdAndExternalSessionIdAndActiveTrue(UUID tenantId, String externalSessionId);
}
