package com.deepthoughtnet.clinic.carepilot.template.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignTemplateRepository extends JpaRepository<CampaignTemplateEntity, UUID> {
    List<CampaignTemplateEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<CampaignTemplateEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
