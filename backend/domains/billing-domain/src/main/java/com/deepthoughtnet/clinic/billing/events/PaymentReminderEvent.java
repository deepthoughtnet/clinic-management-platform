package com.deepthoughtnet.clinic.billing.events;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a bill remains unpaid within a reminder window.
 */
public record PaymentReminderEvent(
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
        PaymentReminderEventPayload payload
) implements ModuleBusinessEvent {
    public static PaymentReminderEvent due(
            UUID tenantId,
            UUID billId,
            UUID patientId,
            String billNumber,
            BigDecimal outstandingAmount,
            String currency,
            String clinicDisplayName,
            String timezone,
            BillStatus billStatus,
            OffsetDateTime billUpdatedAt,
            String reminderWindow,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = billUpdatedAt == null ? OffsetDateTime.now() : billUpdatedAt;
        String correlationId = currentCorrelationId();
        String normalizedWindow = reminderWindow == null || reminderWindow.isBlank() ? "OUTSTANDING" : reminderWindow.trim();
        return new PaymentReminderEvent(
                deterministicEventId("PAYMENT_REMINDER", tenantId, billId, billUpdatedAt, outstandingAmount, normalizedWindow),
                "PAYMENT_REMINDER",
                1,
                occurredAt,
                tenantId,
                "BILLING",
                "BILL",
                billId,
                correlationId,
                correlationId,
                actorId,
                new PaymentReminderEventPayload(
                        billId,
                        patientId,
                        billNumber,
                        outstandingAmount,
                        currency,
                        clinicDisplayName,
                        timezone,
                        billStatus == null ? null : billStatus.name(),
                        billUpdatedAt,
                        normalizedWindow
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
