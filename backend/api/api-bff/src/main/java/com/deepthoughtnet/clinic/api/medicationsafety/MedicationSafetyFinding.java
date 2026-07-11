package com.deepthoughtnet.clinic.api.medicationsafety;

import java.util.List;

public record MedicationSafetyFinding(
        String findingId,
        String ruleCode,
        MedicationSafetyFindingCategory category,
        MedicationSafetySeverity severity,
        String title,
        String summary,
        String clinicalRationale,
        List<String> affectedMedicationIds,
        List<String> affectedMedicineNames,
        List<String> evidence,
        List<String> sourceReferences,
        String verificationStatus,
        boolean acknowledgementRequired,
        boolean overrideAllowed,
        String suggestedDoctorAction,
        List<String> dataQualityNotes
) {
}
