package com.deepthoughtnet.clinic.platform.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventRecord(
        UUID id,
        UUID tenantId,
        String entityType,
        UUID entityId,
        String action,
        UUID actorAppUserId,
        OffsetDateTime occurredAt,
        String summary,
        String detailsJson,
        AuditActorContext actorContext
) {
    public AuditEventRecord(UUID id, UUID tenantId, String entityType, UUID entityId, String action,
                            UUID actorAppUserId, OffsetDateTime occurredAt, String summary, String detailsJson) {
        this(id, tenantId, entityType, entityId, action, actorAppUserId, occurredAt, summary, detailsJson, null);
    }
}
