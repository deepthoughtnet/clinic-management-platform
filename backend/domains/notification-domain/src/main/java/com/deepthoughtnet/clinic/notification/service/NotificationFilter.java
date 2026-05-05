package com.deepthoughtnet.clinic.notification.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationFilter(
        String status,
        String eventType,
        String module,
        String entityType,
        UUID entityId,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
) {
}
