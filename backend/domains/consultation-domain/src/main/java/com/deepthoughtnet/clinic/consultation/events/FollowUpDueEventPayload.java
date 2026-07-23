package com.deepthoughtnet.clinic.consultation.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable notification-safe payload emitted when a consultation follow-up becomes due.
 */
public record FollowUpDueEventPayload(
        UUID consultationId,
        UUID patientId,
        UUID doctorUserId,
        String doctorDisplayName,
        String clinicDisplayName,
        LocalDate followUpDate,
        String timezone,
        String reminderWindow,
        OffsetDateTime dueAt
) implements ModuleBusinessEventPayload {
}
