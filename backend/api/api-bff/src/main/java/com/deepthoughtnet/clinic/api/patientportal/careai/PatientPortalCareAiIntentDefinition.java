package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;

record PatientPortalCareAiIntentDefinition(
        PatientPortalCareAiIntent intentType,
        PatientPortalCareAiIntent mappedWorkflow,
        boolean startsWorkflow,
        boolean interruptsCurrentWorkflow,
        boolean clearsWorkflowState,
        boolean requiresConfirmation,
        int priority,
        List<String> exampleUtterances
) {
}
