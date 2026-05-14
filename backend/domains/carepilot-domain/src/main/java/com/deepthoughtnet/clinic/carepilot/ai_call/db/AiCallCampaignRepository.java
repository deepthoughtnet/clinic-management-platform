package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for tenant-scoped AI call campaigns. */
public interface AiCallCampaignRepository extends JpaRepository<AiCallCampaignEntity, UUID> {
    List<AiCallCampaignEntity> findByTenantId(UUID tenantId);
    Optional<AiCallCampaignEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    long countByTenantId(UUID tenantId);
    long countByTenantIdAndStatus(UUID tenantId, AiCallCampaignStatus status);
}
