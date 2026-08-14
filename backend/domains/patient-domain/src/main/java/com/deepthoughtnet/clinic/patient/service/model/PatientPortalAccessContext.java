package com.deepthoughtnet.clinic.patient.service.model;

public record PatientPortalAccessContext(
        String clinicId,
        String clinicSlug,
        String tenantId,
        String doctorId,
        String appointmentIntent
) {
}
