package com.deepthoughtnet.clinic.consultation.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a patient follow-up becomes due.
 */
public record FollowUpDueEvent(
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
        FollowUpDueEventPayload payload
) implements ModuleBusinessEvent {
    public static FollowUpDueEvent due(
            UUID tenantId,
            UUID consultationId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate followUpDate,
            String timezone,
            String reminderWindow,
            UUID actorId
    ) {
        OffsetDateTime dueAt = followUpDate == null ? OffsetDateTime.now() : followUpDate.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        String correlationId = currentCorrelationId();
        return new FollowUpDueEvent(
                deterministicEventId("FOLLOW_UP_DUE", tenantId, consultationId, followUpDate, reminderWindow),
                "FOLLOW_UP_DUE",
                1,
                dueAt,
                tenantId,
                "CONSULTATION",
                "CONSULTATION",
                consultationId,
                correlationId,
                correlationId,
                actorId,
                new FollowUpDueEventPayload(
                        consultationId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        clinicDisplayName,
                        followUpDate,
                        timezone,
                        reminderWindow,
                        dueAt
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
