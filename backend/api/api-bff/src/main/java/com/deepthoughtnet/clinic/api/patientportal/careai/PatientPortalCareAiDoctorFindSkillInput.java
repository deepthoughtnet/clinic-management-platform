package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiDoctorFindSkillInput(
        String doctorQuery,
        String specialityQuery,
        String clinicSlug,
        String locationQuery,
        String patientId,
        String tenantId
) {
}
