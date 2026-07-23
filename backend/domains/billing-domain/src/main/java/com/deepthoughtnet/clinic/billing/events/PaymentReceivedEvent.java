package com.deepthoughtnet.clinic.billing.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a payment is recorded successfully.
 */
public record PaymentReceivedEvent(
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
        PaymentReceivedEventPayload payload
) implements ModuleBusinessEvent {
    public static PaymentReceivedEvent received(
            UUID tenantId,
            UUID paymentId,
            UUID billId,
            UUID patientId,
            String billNumber,
            String receiptNumber,
            java.math.BigDecimal amount,
            String currency,
            String paymentMethodDisplayName,
            String clinicDisplayName,
            String timezone,
            OffsetDateTime receivedAt,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = receivedAt == null ? OffsetDateTime.now() : receivedAt;
        String correlationId = currentCorrelationId();
        return new PaymentReceivedEvent(
                deterministicEventId("PAYMENT_RECEIVED", tenantId, paymentId, billId, receivedAt, receiptNumber),
                "PAYMENT_RECEIVED",
                1,
                occurredAt,
                tenantId,
                "BILLING",
                "PAYMENT",
                paymentId,
                correlationId,
                correlationId,
                actorId,
                new PaymentReceivedEventPayload(
                        paymentId,
                        billId,
                        patientId,
                        billNumber,
                        receiptNumber,
                        amount,
                        currency,
                        paymentMethodDisplayName,
                        clinicDisplayName,
                        timezone,
                        receivedAt
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
