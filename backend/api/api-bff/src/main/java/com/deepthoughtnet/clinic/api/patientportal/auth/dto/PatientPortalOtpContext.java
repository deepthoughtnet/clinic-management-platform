package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

public record PatientPortalOtpContext(
        String clinicId,
        String clinicSlug,
        String tenantId,
        String doctorId,
        String appointmentIntent
) {
}
