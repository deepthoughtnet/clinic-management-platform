package com.deepthoughtnet.clinic.clinic.service.model;

public record DoctorProfileUpsertCommand(
        String mobile,
        String specialization,
        String qualification,
        String registrationNumber,
        String consultationRoom,
        Boolean active
) {
}
