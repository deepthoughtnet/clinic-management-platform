package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.OffsetDateTime;

public record PatientPortalNotificationResponse(
        String id,
        String eventType,
        String subject,
        String message,
        String status,
        OffsetDateTime readAt,
        String sourceType,
        String sourceId,
        OffsetDateTime createdAt,
        String actionPath
) {
}
