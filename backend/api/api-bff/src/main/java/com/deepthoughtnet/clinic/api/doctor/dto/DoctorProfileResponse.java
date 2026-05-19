package com.deepthoughtnet.clinic.api.doctor.dto;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

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
        BigDecimal consultationFee,
        Integer yearsOfExperience,
        Integer age,
        boolean active,
        OffsetDateTime updatedAt
) {
}
