package com.deepthoughtnet.clinic.notification.service.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationHistoryGroupRecord(
        String logicalNotificationId,
        UUID tenantId,
        UUID patientId,
        String eventType,
        String subject,
        String message,
        String overallStatus,
        String readState,
        OffsetDateTime queuedAt,
        OffsetDateTime lastActivityAt,
        List<NotificationHistoryRecord> deliveries
) {
}
