package com.deepthoughtnet.clinic.platform.modulith.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface ModuleBusinessEvent extends Serializable {
    UUID eventId();

    String eventType();

    int eventVersion();

    OffsetDateTime occurredAt();

    UUID tenantId();

    String sourceModule();

    String aggregateType();

    UUID aggregateId();

    String correlationId();

    String causationId();

    UUID actorId();

    ModuleBusinessEventPayload payload();
}
