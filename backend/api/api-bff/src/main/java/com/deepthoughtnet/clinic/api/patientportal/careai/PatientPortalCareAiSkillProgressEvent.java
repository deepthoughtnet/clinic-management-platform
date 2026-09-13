package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiSkillProgressEvent(
        PatientPortalCareAiExecutionIdentity execution,
        String status,
        String progressKey,
        String acknowledgement
) {
}
