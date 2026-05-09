package com.deepthoughtnet.clinic.api.doctor.dto;

import java.time.OffsetDateTime;

public record DoctorProfileResponse(
        String doctorUserId,
        String doctorName,
        String email,
        String membershipRole,
        String mobile,
        String specialization,
        String qualification,
        String registrationNumber,
        String consultationRoom,
        boolean active,
        OffsetDateTime updatedAt
) {
}
