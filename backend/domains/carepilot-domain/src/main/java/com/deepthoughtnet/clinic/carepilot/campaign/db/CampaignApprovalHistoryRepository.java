package com.deepthoughtnet.clinic.carepilot.campaign.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignApprovalHistoryRepository extends JpaRepository<CampaignApprovalHistoryEntity, UUID> {
    List<CampaignApprovalHistoryEntity> findByTenantIdAndCampaignIdOrderByCreatedAtAsc(UUID tenantId, UUID campaignId);
}
