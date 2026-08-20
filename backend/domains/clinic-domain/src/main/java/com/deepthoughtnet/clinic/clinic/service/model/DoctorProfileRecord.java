package com.deepthoughtnet.clinic.clinic.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
        LocalDate dateOfBirth,
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
                null,
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

    public DoctorProfileRecord(
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
        this(
                id,
                tenantId,
                doctorUserId,
                mobile,
                specialization,
                specializations,
                qualification,
                registrationNumber,
                consultationRoom,
                consultationFee,
                opdFee,
                followUpFee,
                emergencyFee,
                yearsOfExperience,
                age,
                null,
                active,
                publicListingEnabled,
                slug,
                photoUrl,
                photoFileName,
                photoMimeType,
                photoSizeBytes,
                createdAt,
                updatedAt
        );
    }

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
            LocalDate dateOfBirth,
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
                dateOfBirth,
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
