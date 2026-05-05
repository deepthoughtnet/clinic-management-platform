package com.deepthoughtnet.clinic.platform.outbox;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventCommand(
        UUID tenantId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        String deduplicationKey,
        String payloadJson,
        OffsetDateTime availableAt
) {
}
