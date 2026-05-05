package com.deepthoughtnet.clinic.platform.core.outbox;

import java.util.UUID;

/**
 * Minimal abstraction for writing outbox events.
 *
 * Implementations live in edge modules (api-bff) so domains can emit
 * operational signals (e.g., DEVICE_SILENT) without depending on api-bff code.
 */
public interface OutboxEventPublisher {

    UUID enqueue(UUID tenantId,
                 String eventType,
                 String aggregateType,
                 UUID aggregateId,
                 String payloadJson);
}
