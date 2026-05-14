package com.deepthoughtnet.clinic.carepilot.execution.db;

import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for immutable provider webhook delivery events.
 */
public interface CampaignDeliveryEventRepository extends JpaRepository<CampaignDeliveryEventEntity, UUID> {
    java.util.List<CampaignDeliveryEventEntity> findByTenantIdAndReceivedAtBetween(UUID tenantId, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
    List<CampaignDeliveryEventEntity> findByTenantIdAndExecutionIdOrderByEventTimestampAsc(UUID tenantId, UUID executionId);
    List<CampaignDeliveryEventEntity> findByTenantIdAndExecutionIdInOrderByEventTimestampAsc(UUID tenantId, List<UUID> executionIds);

    boolean existsByProviderNameAndProviderMessageIdAndInternalStatusAndEventTypeAndEventTimestamp(
            String providerName,
            String providerMessageId,
            MessageDeliveryStatus internalStatus,
            String eventType,
            OffsetDateTime eventTimestamp
    );
}
