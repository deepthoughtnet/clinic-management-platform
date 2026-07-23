package com.deepthoughtnet.clinic.billing.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable notification-safe payload emitted when an outstanding bill becomes reminder eligible.
 */
public record PaymentReminderEventPayload(
        UUID billId,
        UUID patientId,
        String billNumber,
        BigDecimal outstandingAmount,
        String currency,
        String clinicDisplayName,
        String timezone,
        String billStatus,
        OffsetDateTime billUpdatedAt,
        String reminderWindow
) implements ModuleBusinessEventPayload {
}
