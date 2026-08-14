package com.deepthoughtnet.clinic.patient.service.model;

public record PatientPortalAccessRequestCommand(
        String fullName,
        String mobile,
        String email,
        String note,
        PatientPortalAccessContext context
) {
}
