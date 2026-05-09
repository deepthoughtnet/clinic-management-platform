package com.deepthoughtnet.clinic.clinic.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DoctorProfileRecord(
        UUID id,
        UUID tenantId,
        UUID doctorUserId,
        String mobile,
        String specialization,
        String qualification,
        String registrationNumber,
        String consultationRoom,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
