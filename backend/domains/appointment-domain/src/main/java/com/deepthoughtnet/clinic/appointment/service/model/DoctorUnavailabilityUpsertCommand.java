package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.OffsetDateTime;

public record DoctorUnavailabilityUpsertCommand(
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        DoctorUnavailabilityType type,
        String reason,
        boolean active
) {
}
