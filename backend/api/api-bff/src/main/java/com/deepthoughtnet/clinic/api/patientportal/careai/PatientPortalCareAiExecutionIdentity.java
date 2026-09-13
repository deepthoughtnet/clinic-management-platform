package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiExecutionIdentity(
        String conversationId,
        String turnId,
        String skillExecutionId,
        String skillId,
        boolean readOnly
) {
}
