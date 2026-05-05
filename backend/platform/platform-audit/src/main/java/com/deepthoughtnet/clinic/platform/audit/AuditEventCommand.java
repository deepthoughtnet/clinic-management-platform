package com.deepthoughtnet.clinic.platform.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventCommand(
        UUID tenantId,
        String entityType,
        UUID entityId,
        String action,
        UUID actorAppUserId,
        OffsetDateTime occurredAt,
        String summary,
        String detailsJson
) {
}
