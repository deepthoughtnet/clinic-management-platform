package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityType;
import java.time.OffsetDateTime;

public record DoctorUnavailabilityResponse(
        String id,
        String tenantId,
        String doctorUserId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        DoctorUnavailabilityType type,
        String reason,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
