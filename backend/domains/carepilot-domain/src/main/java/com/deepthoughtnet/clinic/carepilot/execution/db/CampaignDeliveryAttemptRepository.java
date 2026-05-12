package com.deepthoughtnet.clinic.carepilot.execution.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for immutable delivery-attempt audit rows.
 */
public interface CampaignDeliveryAttemptRepository extends JpaRepository<CampaignDeliveryAttemptEntity, UUID> {
    List<CampaignDeliveryAttemptEntity> findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(UUID tenantId, UUID executionId);
}
