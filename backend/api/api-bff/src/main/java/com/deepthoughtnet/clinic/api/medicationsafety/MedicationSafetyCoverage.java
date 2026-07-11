package com.deepthoughtnet.clinic.api.medicationsafety;

public record MedicationSafetyCoverage(
        boolean exactDuplicateEvaluated,
        boolean ingredientDuplicateEvaluated,
        boolean classDuplicateEvaluated,
        boolean allergyEvaluated,
        boolean conditionRulesEvaluated,
        boolean renalEvaluated,
        boolean hepaticEvaluated,
        boolean doseEvaluated,
        boolean interactionEvaluated,
        boolean currentMedicationOverlapEvaluated,
        String renalCoverageStatus
) {
    public boolean allEvaluated() {
        return exactDuplicateEvaluated
                && ingredientDuplicateEvaluated
                && classDuplicateEvaluated
                && allergyEvaluated
                && conditionRulesEvaluated
                && renalEvaluated
                && hepaticEvaluated
                && doseEvaluated
                && interactionEvaluated
                && currentMedicationOverlapEvaluated
                && "EVALUATED".equalsIgnoreCase(renalCoverageStatus);
    }
}
