package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Set;

record PatientPortalCareAiWorkflowSubStateDefinition(
        PatientPortalCareAiWorkflowType workflowType,
        PatientPortalCareAiWorkflowSubState initialState,
        Set<PatientPortalCareAiWorkflowSubState> terminalStates,
        Set<PatientPortalCareAiWorkflowSubState> allowedStates,
        Set<PatientPortalCareAiWorkflowSubStateTransition> allowedTransitions,
        String description
) {
}
