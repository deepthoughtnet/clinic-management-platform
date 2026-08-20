package com.deepthoughtnet.clinic.clinic.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DoctorProfileUpsertCommand(
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
        Boolean active,
        Boolean publicListingEnabled,
        String slug,
        String doctorName
) {
    public DoctorProfileUpsertCommand(
            String mobile,
            String specialization,
            String qualification,
            String registrationNumber,
            String consultationRoom,
            BigDecimal consultationFee,
            Integer yearsOfExperience,
            Integer age,
            Boolean active,
            Boolean publicListingEnabled,
            String slug
    ) {
        this(mobile, specialization, null, qualification, registrationNumber, consultationRoom, consultationFee, consultationFee, null, null, yearsOfExperience, age, null, active, publicListingEnabled, slug, null);
    }

    public DoctorProfileUpsertCommand(
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
            Boolean active,
            Boolean publicListingEnabled,
            String slug
    ) {
        this(mobile, specialization, specializations, qualification, registrationNumber, consultationRoom, consultationFee, opdFee, followUpFee, emergencyFee, yearsOfExperience, age, dateOfBirth, active, publicListingEnabled, slug, null);
    }
}
