package com.deepthoughtnet.clinic.api.medicationsafety;

public record MedicationSafetyFindingReviewDecision(
        String findingId,
        String ruleCode,
        boolean acknowledged,
        boolean overrideApplied,
        String reasonCode,
        String reasonText
) {
}
