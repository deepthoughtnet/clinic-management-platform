package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Explicit channel context for AIVA execution; this is not an authentication context. */
public record AivaExecutionContext(
        UUID tenantId,
        UUID patientId,
        String channel,
        String language,
        ZoneId timezone,
        String sessionId,
        UUID interactionId,
        UUID clinicId,
        UUID locationId,
        Set<String> capabilities
) {
    public AivaExecutionContext {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel is required");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        timezone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
