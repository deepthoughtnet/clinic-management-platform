package com.deepthoughtnet.clinic.api.patientportal.dto;

public record PatientPortalDoctorResponse(
        String publicDoctorId,
        String doctorName,
        String specialization,
        String qualification,
        String consultationRoom,
        Integer yearsOfExperience
) {
}
