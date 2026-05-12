package com.deepthoughtnet.clinic.messaging.spi;

import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral message dispatch request.
 */
public record MessageRequest(
        UUID tenantId,
        MessageChannel channel,
        MessageRecipient recipient,
        String subject,
        String body,
        UUID templateId,
        String correlationId,
        UUID campaignId,
        UUID executionId,
        Map<String, String> metadata
) {
    public MessageRequest {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (recipient == null) {
            throw new IllegalArgumentException("recipient is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
