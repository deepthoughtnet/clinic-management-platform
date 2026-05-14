package com.deepthoughtnet.clinic.carepilot.execution.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for immutable delivery-attempt audit rows.
 */
public interface CampaignDeliveryAttemptRepository extends JpaRepository<CampaignDeliveryAttemptEntity, UUID> {
    java.util.List<CampaignDeliveryAttemptEntity> findByTenantIdAndAttemptedAtBetween(UUID tenantId, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
    List<CampaignDeliveryAttemptEntity> findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(UUID tenantId, UUID executionId);
    List<CampaignDeliveryAttemptEntity> findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(UUID tenantId, List<UUID> executionIds);
    java.util.Optional<CampaignDeliveryAttemptEntity> findFirstByTenantIdAndExecutionIdOrderByAttemptNumberDesc(UUID tenantId, UUID executionId);
}
