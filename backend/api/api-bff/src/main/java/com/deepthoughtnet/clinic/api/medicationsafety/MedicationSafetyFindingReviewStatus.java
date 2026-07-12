package com.deepthoughtnet.clinic.api.medicationsafety;

public record MedicationSafetyFindingReviewStatus(
        String findingId,
        String ruleCode,
        String title,
        String category,
        String severity,
        boolean acknowledgementRequired,
        boolean overrideRequired,
        boolean acknowledged,
        boolean overrideApplied,
        String reasonCode,
        String reasonText
) {
}
