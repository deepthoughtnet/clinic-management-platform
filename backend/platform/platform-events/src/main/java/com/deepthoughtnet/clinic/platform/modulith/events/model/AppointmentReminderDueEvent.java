package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentReminderDueEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        UUID tenantId,
        String sourceModule,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId,
        UUID actorId,
        AppointmentReminderDueEventPayload payload
) implements ModuleBusinessEvent {
}
