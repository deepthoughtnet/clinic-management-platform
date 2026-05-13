package com.deepthoughtnet.clinic.carepilot.execution.db;

import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignExecutionRepository extends JpaRepository<CampaignExecutionEntity, UUID> {
    List<CampaignExecutionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<CampaignExecutionEntity> findByTenantIdAndStatusInOrderByUpdatedAtDesc(UUID tenantId, Collection<ExecutionStatus> statuses);
    Optional<CampaignExecutionEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
            UUID tenantId,
            UUID campaignId,
            UUID recipientPatientId,
            com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType channelType,
            OffsetDateTime fromScheduledAt,
            OffsetDateTime toScheduledAt
    );
    List<CampaignExecutionEntity> findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            Collection<ExecutionStatus> statuses,
            OffsetDateTime scheduledAt
    );

    boolean existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
            UUID tenantId,
            UUID campaignId,
            UUID sourceReferenceId,
            String reminderWindow,
            com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType channelType
    );

    Optional<CampaignExecutionEntity> findFirstByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
            UUID tenantId,
            UUID campaignId,
            UUID sourceReferenceId,
            String reminderWindow,
            com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType channelType
    );

    List<CampaignExecutionEntity> findByTenantIdAndScheduledAtBetweenOrderByScheduledAtDesc(
            UUID tenantId,
            OffsetDateTime scheduledFrom,
            OffsetDateTime scheduledTo
    );

    List<CampaignExecutionEntity> findByTenantIdAndCampaignIdAndScheduledAtBetweenOrderByScheduledAtDesc(
            UUID tenantId,
            UUID campaignId,
            OffsetDateTime scheduledFrom,
            OffsetDateTime scheduledTo
    );

    List<CampaignExecutionEntity> findByTenantIdAndStatusInAndScheduledAtBetweenOrderByUpdatedAtDesc(
            UUID tenantId,
            Collection<ExecutionStatus> statuses,
            OffsetDateTime scheduledFrom,
            OffsetDateTime scheduledTo
    );

    List<CampaignExecutionEntity> findByTenantIdAndCampaignIdAndStatusInAndScheduledAtBetweenOrderByUpdatedAtDesc(
            UUID tenantId,
            UUID campaignId,
            Collection<ExecutionStatus> statuses,
            OffsetDateTime scheduledFrom,
            OffsetDateTime scheduledTo
    );

    List<CampaignExecutionEntity> findByTenantIdAndCampaignIdOrderByUpdatedAtDesc(UUID tenantId, UUID campaignId);

    List<CampaignExecutionEntity> findTop50ByTenantIdAndCampaignIdOrderByUpdatedAtDesc(UUID tenantId, UUID campaignId);

    Optional<CampaignExecutionEntity> findFirstByTenantIdAndCampaignIdAndStatusInOrderByScheduledAtAsc(
            UUID tenantId,
            UUID campaignId,
            Collection<ExecutionStatus> statuses
    );

    List<CampaignExecutionEntity> findByProviderNameAndProviderMessageIdOrderByUpdatedAtDesc(
            String providerName,
            String providerMessageId
    );
}
