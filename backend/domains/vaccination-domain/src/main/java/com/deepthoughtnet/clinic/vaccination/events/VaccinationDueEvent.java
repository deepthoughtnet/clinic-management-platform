package com.deepthoughtnet.clinic.vaccination.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Immutable business fact emitted when a vaccination schedule entry becomes due.
 */
public record VaccinationDueEvent(
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
        VaccinationDueEventPayload payload
) implements ModuleBusinessEvent {
    public static VaccinationDueEvent due(
            UUID tenantId,
            UUID vaccinationScheduleEntryId,
            UUID patientId,
            String vaccineDisplayName,
            String doseDisplayName,
            LocalDate dueDate,
            String timezone,
            String clinicDisplayName,
            String reminderWindow,
            UUID actorId
    ) {
        OffsetDateTime dueAt = dueDate == null ? OffsetDateTime.now() : dueDate.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        String correlationId = currentCorrelationId();
        return new VaccinationDueEvent(
                deterministicEventId("VACCINATION_DUE", tenantId, vaccinationScheduleEntryId, dueDate, reminderWindow),
                "VACCINATION_DUE",
                1,
                dueAt,
                tenantId,
                "VACCINATION",
                "PATIENT_VACCINATION",
                vaccinationScheduleEntryId,
                correlationId,
                correlationId,
                actorId,
                new VaccinationDueEventPayload(
                        vaccinationScheduleEntryId,
                        patientId,
                        vaccineDisplayName,
                        doseDisplayName,
                        dueDate,
                        timezone,
                        clinicDisplayName,
                        reminderWindow,
                        dueAt
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
