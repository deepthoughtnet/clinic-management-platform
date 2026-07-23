package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

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
            String orderNumber,
            String clinicDisplayName,
            String timezone,
            OffsetDateTime publishedAt,
            String reportFilename,
            String deliveryStatus,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = publishedAt == null ? OffsetDateTime.now() : publishedAt;
        String correlationId = currentCorrelationId();
        return new LabReportPublishedEvent(
                deterministicEventId("LAB_REPORT_PUBLISHED", tenantId, labOrderId, orderNumber, publishedAt),
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
                        orderNumber,
                        clinicDisplayName,
                        timezone,
                        publishedAt,
                        reportFilename,
                        deliveryStatus
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
