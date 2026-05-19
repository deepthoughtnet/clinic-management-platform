package com.deepthoughtnet.clinic.clinic.service.model;

import java.math.BigDecimal;

public record DoctorProfileUpsertCommand(
        String mobile,
        String specialization,
        String qualification,
        String registrationNumber,
        String consultationRoom,
        BigDecimal consultationFee,
        Integer yearsOfExperience,
        Integer age,
        Boolean active
) {
}
