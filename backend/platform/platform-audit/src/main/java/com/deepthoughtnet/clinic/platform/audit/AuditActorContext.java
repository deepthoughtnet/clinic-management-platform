package com.deepthoughtnet.clinic.platform.audit;

import java.util.UUID;

/** Truthful, channel-neutral actor attribution; actorId is absent for non-user actors. */
public record AuditActorContext(String actorType, String actorId, String channel,
                                UUID interactionId, String correlationId) {
    public AuditActorContext {
        if (actorType == null || actorType.isBlank()) throw new IllegalArgumentException("actorType is required");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel is required");
    }
}
