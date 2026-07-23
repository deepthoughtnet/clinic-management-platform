package com.deepthoughtnet.clinic.billing.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable notification-safe payload emitted when a patient-visible bill is issued.
 */
public record BillGeneratedEventPayload(
        UUID billId,
        UUID patientId,
        String billNumber,
        BigDecimal amount,
        String currency,
        LocalDate dueAt,
        String clinicDisplayName,
        String timezone,
        OffsetDateTime issuedAt
) implements ModuleBusinessEventPayload {
}
