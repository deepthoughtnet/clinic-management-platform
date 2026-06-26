package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiWorkflowRouteDecision(
        PatientPortalCareAiIntent targetWorkflow,
        boolean shouldSwitch,
        boolean shouldResetState,
        String reason
) {
}
