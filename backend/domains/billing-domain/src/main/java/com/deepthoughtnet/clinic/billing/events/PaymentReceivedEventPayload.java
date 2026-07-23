package com.deepthoughtnet.clinic.billing.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable notification-safe payload emitted when a payment is persisted successfully.
 */
public record PaymentReceivedEventPayload(
        UUID paymentId,
        UUID billId,
        UUID patientId,
        String billNumber,
        String receiptNumber,
        BigDecimal amount,
        String currency,
        String paymentMethodDisplayName,
        String clinicDisplayName,
        String timezone,
        OffsetDateTime receivedAt
) implements ModuleBusinessEventPayload {
}
