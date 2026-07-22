package com.deepthoughtnet.clinic.api.lab.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a lab report is published.
 * <p>
 * The current authoritative lab workflow is owned by the API application, so the
 * concrete contract lives beside that workflow rather than in the generic event store.
 */
public record LabReportPublishedEvent(
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
        LabReportPublishedEventPayload payload
) implements ModuleBusinessEvent {
    public static LabReportPublishedEvent published(
            UUID tenantId,
            UUID labOrderId,
            UUID patientId,
            UUID consultationId,
            String reportFilename,
            String deliveryStatus,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new LabReportPublishedEvent(
                deterministicEventId("LAB_REPORT_PUBLISHED", tenantId, labOrderId),
                "LAB_REPORT_PUBLISHED",
                1,
                occurredAt,
                tenantId,
                "LAB",
                "LAB_ORDER",
                labOrderId,
                correlationId,
                correlationId,
                actorId,
                new LabReportPublishedEventPayload(
                        labOrderId,
                        patientId,
                        consultationId,
                        reportFilename,
                        deliveryStatus
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
