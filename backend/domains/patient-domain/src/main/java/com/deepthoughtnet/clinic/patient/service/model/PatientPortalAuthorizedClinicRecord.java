package com.deepthoughtnet.clinic.patient.service.model;

import java.util.UUID;

public record PatientPortalAuthorizedClinicRecord(
        UUID tenantId,
        String tenantCode,
        String clinicName,
        UUID patientId,
        String patientDisplayName,
        boolean active,
        String authorizationSource
) {
}
