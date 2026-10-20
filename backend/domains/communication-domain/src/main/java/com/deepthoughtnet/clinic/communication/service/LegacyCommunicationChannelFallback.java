package com.deepthoughtnet.clinic.communication.service;

import java.util.Optional;
import java.util.UUID;

/** Explicit compatibility bridge for process-level local/UAT provider configuration. */
public interface LegacyCommunicationChannelFallback {
    boolean enabled();
    Optional<CommunicationChannelModels.Resolution> resolveInbound(String normalizedDid);
    Optional<CommunicationChannelModels.Resolution> resolveOutbound(UUID tenantId, UUID clinicId, UUID locationId);
}
