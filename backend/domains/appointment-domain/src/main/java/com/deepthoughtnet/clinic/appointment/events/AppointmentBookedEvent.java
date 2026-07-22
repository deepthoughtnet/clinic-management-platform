package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when an appointment is booked.
 * <p>
 * The appointment module owns the vocabulary and payload shape; the platform
 * event infrastructure only persists and dispatches this contract.
 */
public record AppointmentBookedEvent(
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
        AppointmentBookedEventPayload payload
) implements ModuleBusinessEvent {
    public static AppointmentBookedEvent booked(
            UUID tenantId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String appointmentTimezone,
            String appointmentStatus,
            String appointmentType,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new AppointmentBookedEvent(
                deterministicEventId("APPOINTMENT_BOOKED", tenantId, appointmentId),
                "APPOINTMENT_BOOKED",
                1,
                occurredAt,
                tenantId,
                "APPOINTMENT",
                "APPOINTMENT",
                appointmentId,
                correlationId,
                correlationId,
                actorId,
                new AppointmentBookedEventPayload(
                        appointmentId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        appointmentStatus,
                        appointmentType
                )
        );
    }

    private static UUID deterministicEventId(String eventType, UUID tenantId, UUID aggregateId) {
        String seed = String.join("|",
                eventType == null ? "" : eventType.trim(),
                tenantId == null ? "" : tenantId.toString(),
                aggregateId == null ? "" : aggregateId.toString());
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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
