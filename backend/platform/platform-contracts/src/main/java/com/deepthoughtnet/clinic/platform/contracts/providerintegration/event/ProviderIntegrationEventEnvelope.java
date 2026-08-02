package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProviderIntegrationEventEnvelope<T extends ProviderIntegrationEventPayload>(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        String producer,
        ProviderSourceReference sourceReference,
        String tenantReference,
        UUID correlationId,
        UUID causationId,
        T payload
) implements Serializable {
}
