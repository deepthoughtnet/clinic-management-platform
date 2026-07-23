package com.deepthoughtnet.clinic.vaccination.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable notification-safe payload emitted when a vaccination dose becomes due.
 */
public record VaccinationDueEventPayload(
        UUID vaccinationScheduleEntryId,
        UUID patientId,
        String vaccineDisplayName,
        String doseDisplayName,
        LocalDate dueDate,
        String timezone,
        String clinicDisplayName,
        String reminderWindow,
        OffsetDateTime dueAt
) implements ModuleBusinessEventPayload {
}
