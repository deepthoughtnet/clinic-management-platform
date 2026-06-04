package com.deepthoughtnet.clinic.api.patientportal.auth;

import java.util.Set;
import java.util.UUID;

public record PatientPortalSessionPrincipal(
        String subject,
        UUID tenantId,
        UUID patientId,
        String phone,
        String displayName,
        Set<String> roles
) {
}
