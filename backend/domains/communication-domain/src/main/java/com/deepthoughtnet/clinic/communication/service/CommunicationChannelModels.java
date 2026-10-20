package com.deepthoughtnet.clinic.communication.service;

import com.deepthoughtnet.clinic.communication.model.CommunicationChannelStatus;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class CommunicationChannelModels {
    private CommunicationChannelModels() {}
    public record UpsertCommand(UUID clinicId, UUID locationId, CommunicationChannelType channelType, String provider,
            String address, String displayName, String trunk, String providerConfigRef, boolean enabled,
            boolean inboundEnabled, boolean outboundEnabled, boolean defaultOutbound, Integer priority) {}
    public record Record(UUID id, UUID tenantId, UUID clinicId, UUID locationId, CommunicationChannelType channelType,
            String provider, String address, String normalizedAddress, String displayName, String trunk,
            String providerConfigRef, boolean enabled, boolean inboundEnabled, boolean outboundEnabled,
            boolean defaultOutbound, Integer priority, CommunicationChannelStatus status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record Resolution(UUID channelId, UUID tenantId, UUID clinicId, UUID locationId, String provider,
            String normalizedAddress, String trunk, String providerConfigRef) {}
}
