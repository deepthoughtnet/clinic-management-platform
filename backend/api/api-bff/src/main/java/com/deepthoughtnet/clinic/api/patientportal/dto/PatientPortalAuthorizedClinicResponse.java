package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.util.UUID;

public record PatientPortalAuthorizedClinicResponse(
        UUID tenantId,
        String tenantCode,
        String clinicName,
        UUID patientId,
        String patientDisplayName,
        boolean active,
        boolean selected,
        String authorizationSource
) {
}
