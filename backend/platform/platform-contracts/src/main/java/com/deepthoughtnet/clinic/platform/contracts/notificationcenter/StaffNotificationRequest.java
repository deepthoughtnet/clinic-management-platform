package com.deepthoughtnet.clinic.platform.contracts.notificationcenter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StaffNotificationRequest(
        UUID tenantId,
        UUID sourceEventId,
        String sourceEventType,
        String sourceModule,
        String aggregateType,
        UUID aggregateId,
        String title,
        String preview,
        NotificationCategory category,
        NotificationPriority priority,
        String businessReference,
        StaffNotificationAction action,
        List<NotificationAudience> audiences,
        OffsetDateTime occurredAt,
        String correlationId,
        String causationId
) {
    public StaffNotificationRequest {
        audiences = audiences == null ? List.of() : List.copyOf(audiences);
    }
}
