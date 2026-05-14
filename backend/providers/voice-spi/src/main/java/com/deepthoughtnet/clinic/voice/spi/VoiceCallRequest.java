package com.deepthoughtnet.clinic.voice.spi;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Provider-neutral outbound voice request contract. */
public record VoiceCallRequest(
        UUID tenantId,
        UUID campaignId,
        UUID executionId,
        String phoneNumber,
        String script,
        OffsetDateTime scheduledAt,
        Map<String, String> metadata
) {
    public VoiceCallRequest {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
