package com.deepthoughtnet.clinic.api.doctor.dto;

import java.math.BigDecimal;
import java.util.List;

public record DoctorProfileRequest(
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
        String dateOfBirth,
        Boolean active,
        Boolean publicListingEnabled,
        String slug
) {
}
