package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;
import java.util.Set;

record PatientPortalCareAiEntityDefinition(
        PatientPortalCareAiEntityType entityType,
        String description,
        List<String> exampleUtterances,
        Set<String> aliases,
        double defaultConfidence
) {
}
