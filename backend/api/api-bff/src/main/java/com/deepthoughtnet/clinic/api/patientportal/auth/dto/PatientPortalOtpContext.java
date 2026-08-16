package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientPortalOtpContext(
        String clinicId,
        @Size(max = 60)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{0,59}$", message = "Clinic slug must use letters, numbers, and hyphens only.")
        String clinicSlug,
        String tenantId,
        String doctorId,
        String appointmentIntent
) {
}
