package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when an appointment reminder becomes due.
 */
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
    public static AppointmentReminderDueEvent due(
            UUID tenantId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String appointmentTimezone,
            String reminderWindow,
            int appointmentVersion,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new AppointmentReminderDueEvent(
                deterministicEventId(
                        "APPOINTMENT_REMINDER_DUE",
                        tenantId,
                        appointmentId,
                        appointmentVersion,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        reminderWindow
                ),
                "APPOINTMENT_REMINDER_DUE",
                1,
                occurredAt,
                tenantId,
                "APPOINTMENT",
                "APPOINTMENT",
                appointmentId,
                correlationId,
                correlationId,
                actorId,
                new AppointmentReminderDueEventPayload(
                        appointmentId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        clinicDisplayName,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        reminderWindow,
                        appointmentVersion
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
