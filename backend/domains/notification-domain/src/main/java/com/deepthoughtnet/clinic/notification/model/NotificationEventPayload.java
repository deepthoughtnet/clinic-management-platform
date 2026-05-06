package com.deepthoughtnet.clinic.notification.model;

import java.util.List;
import java.util.UUID;

public record NotificationEventPayload(
        UUID historyId,
        String notificationType,
        List<String> recipientRoles,
        String recipient,
        String channel,
        String subject,
        String body,
        String auditAction,
        String detailsJson,
        UUID patientId,
        String sourceType,
        UUID sourceId
) {
    public NotificationEventPayload(
            String notificationType,
            List<String> recipientRoles,
            String subject,
            String body,
            String auditAction,
            String detailsJson
    ) {
        this(null, notificationType, recipientRoles, null, null, subject, body, auditAction, detailsJson, null, null, null);
    }
}
