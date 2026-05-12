package com.deepthoughtnet.clinic.carepilot.campaign.db;

import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {
    List<CampaignEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<CampaignEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<CampaignEntity> findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(UUID tenantId, CampaignType campaignType);
}
