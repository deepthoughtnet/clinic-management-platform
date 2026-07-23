package com.deepthoughtnet.clinic.billing.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a bill is issued for the patient.
 */
public record BillGeneratedEvent(
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
        BillGeneratedEventPayload payload
) implements ModuleBusinessEvent {
    public static BillGeneratedEvent generated(
            UUID tenantId,
            UUID billId,
            UUID patientId,
            String billNumber,
            java.math.BigDecimal amount,
            String currency,
            LocalDate dueAt,
            String clinicDisplayName,
            String timezone,
            OffsetDateTime issuedAt,
            int version,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = issuedAt == null ? OffsetDateTime.now() : issuedAt;
        String correlationId = currentCorrelationId();
        return new BillGeneratedEvent(
                deterministicEventId("BILL_GENERATED", tenantId, billId, version, issuedAt, billNumber),
                "BILL_GENERATED",
                1,
                occurredAt,
                tenantId,
                "BILLING",
                "BILL",
                billId,
                correlationId,
                correlationId,
                actorId,
                new BillGeneratedEventPayload(
                        billId,
                        patientId,
                        billNumber,
                        amount,
                        currency,
                        dueAt,
                        clinicDisplayName,
                        timezone,
                        issuedAt
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
