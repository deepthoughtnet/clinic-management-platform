package com.deepthoughtnet.clinic.patient.service.model;

import java.util.UUID;

public record PatientPortalAccessGrantRecord(
        UUID tenantId,
        String tenantCode,
        UUID patientId,
        String patientDisplayName,
        String patientMobile,
        String subject
) {
}
