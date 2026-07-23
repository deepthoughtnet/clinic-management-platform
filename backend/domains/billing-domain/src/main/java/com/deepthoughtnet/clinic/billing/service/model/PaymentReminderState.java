package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Immutable snapshot of the current bill state relevant to payment reminder validation.
 *
 * @param found whether the bill exists for the tenant and identifier
 * @param reminderEligible whether the current bill is still eligible for reminders
 * @param outstandingAmount current outstanding amount
 * @param billStatus current bill status name
 * @param updatedAt current bill update timestamp
 * @param version current bill optimistic-lock version
 */
public record PaymentReminderState(
        boolean found,
        boolean reminderEligible,
        BigDecimal outstandingAmount,
        String billStatus,
        OffsetDateTime updatedAt,
        long version
) {
    public static PaymentReminderState missing() {
        return new PaymentReminderState(false, false, null, null, null, 0L);
    }
}
