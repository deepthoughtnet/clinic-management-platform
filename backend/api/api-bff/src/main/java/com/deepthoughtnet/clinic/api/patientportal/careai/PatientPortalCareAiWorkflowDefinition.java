package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Set;

record PatientPortalCareAiWorkflowDefinition(
        PatientPortalCareAiWorkflowType workflowType,
        PatientPortalCareAiIntent entryIntent,
        Set<PatientPortalCareAiWorkflowEntity> requiredEntities,
        Set<PatientPortalCareAiWorkflowEntity> optionalEntities,
        boolean confirmationRequired,
        boolean supportsInterruption,
        Set<String> terminalStatuses,
        Set<PatientPortalCareAiWorkflowType> allowedTransitions,
        String description
) {
}
