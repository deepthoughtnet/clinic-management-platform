package com.deepthoughtnet.clinic.notification.model;

import java.util.List;

public record NotificationEventPayload(
        String notificationType,
        List<String> recipientRoles,
        String subject,
        String body,
        String auditAction,
        String detailsJson
) {
}
