package com.deepthoughtnet.clinic.clinic.service.model;

import java.math.BigDecimal;
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
        Boolean active,
        Boolean publicListingEnabled,
        String slug
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
        this(mobile, specialization, null, qualification, registrationNumber, consultationRoom, consultationFee, consultationFee, null, null, yearsOfExperience, age, active, publicListingEnabled, slug);
    }
}
