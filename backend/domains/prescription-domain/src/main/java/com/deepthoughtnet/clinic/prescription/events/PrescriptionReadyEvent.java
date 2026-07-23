package com.deepthoughtnet.clinic.prescription.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a prescription is finalized for the patient.
 */
public record PrescriptionReadyEvent(
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
        PrescriptionReadyEventPayload payload
) implements ModuleBusinessEvent {
    public static PrescriptionReadyEvent ready(
            UUID tenantId,
            UUID prescriptionId,
            UUID consultationId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            String clinicDisplayName,
            String prescriptionNumber,
            LocalDate followUpDate,
            String timezone,
            OffsetDateTime finalizedAt,
            int versionNumber,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = finalizedAt == null ? OffsetDateTime.now() : finalizedAt;
        String correlationId = currentCorrelationId();
        return new PrescriptionReadyEvent(
                deterministicEventId("PRESCRIPTION_READY", tenantId, prescriptionId, versionNumber, finalizedAt, prescriptionNumber),
                "PRESCRIPTION_READY",
                1,
                occurredAt,
                tenantId,
                "PRESCRIPTION",
                "PRESCRIPTION",
                prescriptionId,
                correlationId,
                correlationId,
                actorId,
                new PrescriptionReadyEventPayload(
                        prescriptionId,
                        consultationId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        clinicDisplayName,
                        prescriptionNumber,
                        followUpDate,
                        timezone,
                        finalizedAt,
                        versionNumber
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
