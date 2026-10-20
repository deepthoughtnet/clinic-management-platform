package com.deepthoughtnet.clinic.communication.db;

import com.deepthoughtnet.clinic.communication.model.CommunicationChannelStatus;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationChannelRepository extends JpaRepository<CommunicationChannelEntity, UUID> {
    List<CommunicationChannelEntity> findByTenantIdOrderByPriorityAscCreatedAtAsc(UUID tenantId);
    Optional<CommunicationChannelEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<CommunicationChannelEntity> findByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatus(
            String normalizedAddress, CommunicationChannelType type, CommunicationChannelStatus status);
    List<CommunicationChannelEntity> findByTenantIdAndChannelTypeAndEnabledTrueAndOutboundEnabledTrueAndStatus(
            UUID tenantId, CommunicationChannelType type, CommunicationChannelStatus status);
    boolean existsByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatusAndIdNot(
            String normalizedAddress, CommunicationChannelType type, CommunicationChannelStatus status, UUID id);
}
