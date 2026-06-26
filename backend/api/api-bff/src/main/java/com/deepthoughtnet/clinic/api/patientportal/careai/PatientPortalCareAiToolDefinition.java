package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Set;

record PatientPortalCareAiToolDefinition(
        PatientPortalCareAiToolType toolType,
        String description,
        Set<PatientPortalCareAiEntityType> requiredEntities,
        Set<PatientPortalCareAiEntityType> optionalEntities,
        boolean confirmationRequired,
        boolean auditRequired,
        String mappedServiceName
) {
}
