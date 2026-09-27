package com.deepthoughtnet.clinic.carepilot.messaging.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Provider-neutral context passed from Engage execution to a reminder channel adapter. */
public record ReminderCommunicationRequest(
        UUID tenantId,
        UUID campaignId,
        UUID executionId,
        String destination,
        String language,
        String scenario,
        String message,
        OffsetDateTime scheduledAt,
        Map<String, String> metadata
) {
    public ReminderCommunicationRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (tenantId == null || executionId == null) {
            throw new IllegalArgumentException("tenantId and executionId are required");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}
