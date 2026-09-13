package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.UUID;

/** Provider identity and owning Care scope exposed to the V2 orchestration adapter. */
public record PatientPortalCareAiDoctorOption(
        String publicDoctorId,
        String doctorName,
        String specialization,
        UUID doctorProfileId,
        UUID clinicId,
        UUID tenantId,
        String clinicSlug,
        String clinicName
) {
}
