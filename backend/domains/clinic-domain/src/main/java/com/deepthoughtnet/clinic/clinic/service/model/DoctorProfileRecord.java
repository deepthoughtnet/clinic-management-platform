package com.deepthoughtnet.clinic.clinic.service.model;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
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
        BigDecimal consultationFee,
        Integer yearsOfExperience,
        Integer age,
        boolean active,
        boolean publicListingEnabled,
        String slug,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
