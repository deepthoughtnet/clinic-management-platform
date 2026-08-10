package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

public record DoctorAvailabilityChangedEvent(
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
        DoctorAvailabilityChangedEventPayload payload
) implements ModuleBusinessEvent {
    public static DoctorAvailabilityChangedEvent changed(
            UUID tenantId,
            UUID availabilityId,
            UUID doctorUserId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            boolean active,
            String action,
            String reason,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        String normalizedAction = action == null ? "CHANGED" : action.trim().toUpperCase();
        return new DoctorAvailabilityChangedEvent(
                deterministicEventId("DOCTOR_AVAILABILITY_CHANGED", tenantId, availabilityId, normalizedAction, active, dayOfWeek, startTime, endTime),
                "DOCTOR_AVAILABILITY_CHANGED",
                1,
                occurredAt,
                tenantId,
                "APPOINTMENT",
                "DOCTOR_AVAILABILITY",
                availabilityId,
                correlationId,
                correlationId,
                actorId,
                new DoctorAvailabilityChangedEventPayload(
                        availabilityId,
                        doctorUserId,
                        normalizedAction,
                        reason,
                        active,
                        dayOfWeek,
                        startTime,
                        endTime
                )
        );
    }

    private static UUID deterministicEventId(Object... parts) {
        StringBuilder seed = new StringBuilder();
        for (Object part : parts) {
            if (seed.length() > 0) {
                seed.append('|');
            }
            seed.append(part == null ? "" : part.toString());
        }
        return UUID.nameUUIDFromBytes(seed.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("X-Correlation-ID");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }
}
