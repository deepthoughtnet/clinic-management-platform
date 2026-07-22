package com.deepthoughtnet.clinic.carepilot.lead.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a lead is converted to a patient.
 */
public record LeadConvertedEvent(
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
        LeadConvertedEventPayload payload
) implements ModuleBusinessEvent {
    public static LeadConvertedEvent converted(
            UUID tenantId,
            UUID leadId,
            UUID patientId,
            boolean createdNewPatient,
            UUID bookedAppointmentId,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new LeadConvertedEvent(
                deterministicEventId("LEAD_CONVERTED", tenantId, leadId),
                "LEAD_CONVERTED",
                1,
                occurredAt,
                tenantId,
                "ENGAGE",
                "LEAD",
                leadId,
                correlationId,
                correlationId,
                actorId,
                new LeadConvertedEventPayload(
                        leadId,
                        patientId,
                        createdNewPatient,
                        bookedAppointmentId
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
