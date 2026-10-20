package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Map;
import java.util.UUID;

/** Channel-neutral planner context. This is not authentication or a portal session. */
public record AivaConversationContext(
        String sessionId,
        String channel,
        UUID tenantId,
        String language,
        String traceId,
        Map<String, String> attributes
) {
    public AivaConversationContext {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
