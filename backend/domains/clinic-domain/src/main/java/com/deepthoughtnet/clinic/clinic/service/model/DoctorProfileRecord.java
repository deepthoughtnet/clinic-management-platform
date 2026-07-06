package com.deepthoughtnet.clinic.clinic.service.model;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DoctorProfileRecord(
        UUID id,
        UUID tenantId,
        UUID doctorUserId,
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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public DoctorProfileRecord(
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
        this(
                id,
                tenantId,
                doctorUserId,
                mobile,
                specialization,
                splitSpecializations(specialization),
                qualification,
                registrationNumber,
                consultationRoom,
                consultationFee,
                consultationFee,
                null,
                null,
                yearsOfExperience,
                age,
                active,
                publicListingEnabled,
                slug,
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt
        );
    }

    private static List<String> splitSpecializations(String specialization) {
        if (specialization == null || specialization.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(specialization.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
