package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;

record PatientPortalCareAiAvailabilityCheckSkillInput(
        String bookingReference,
        String publicDoctorId,
        String clinicSlug,
        String tenantId,
        String clinicId,
        LocalDate date
) {
}
