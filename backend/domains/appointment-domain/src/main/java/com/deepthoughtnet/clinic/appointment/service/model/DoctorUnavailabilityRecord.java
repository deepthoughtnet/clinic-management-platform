package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DoctorUnavailabilityRecord(
        UUID id,
        UUID tenantId,
        UUID doctorUserId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        DoctorUnavailabilityType type,
        String reason,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
