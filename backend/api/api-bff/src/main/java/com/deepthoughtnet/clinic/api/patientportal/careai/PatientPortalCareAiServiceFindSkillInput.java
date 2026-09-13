package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiServiceFindSkillInput(
        String serviceQuery,
        String clinicSlug,
        String locationQuery,
        String patientId,
        String tenantId
) {
}
