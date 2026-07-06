package com.deepthoughtnet.clinic.api.doctor.dto;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;

public record DoctorProfileResponse(
        String doctorUserId,
        String doctorName,
        String email,
        String membershipRole,
        String mobile,
        String specialization,
        List<String> specializations,
        String qualification,
        String registrationNumber,
        String consultationRoom,
        BigDecimal consultationFee,
        BigDecimal opdFee,
        BigDecimal followUpFee,
        BigDecimal emergencyFee,
        Integer yearsOfExperience,
        Integer age,
        boolean active,
        boolean publicListingEnabled,
        String slug,
        String photoUrl,
        String photoFileName,
        String photoMimeType,
        Long photoSizeBytes,
        OffsetDateTime updatedAt
) {
}
