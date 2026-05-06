package com.deepthoughtnet.clinic.notification.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationHistoryRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String eventType,
        String channel,
        String recipient,
        String subject,
        String message,
        String status,
        String failureReason,
        String sourceType,
        UUID sourceId,
        String deduplicationKey,
        UUID outboxEventId,
        int attemptCount,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
