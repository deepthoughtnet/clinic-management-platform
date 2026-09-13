package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiClinicFindSkillInput(
        String clinicQuery,
        String specialityQuery,
        String locationQuery,
        String patientId,
        String tenantId
) {
}
