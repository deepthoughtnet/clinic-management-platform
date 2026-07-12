package com.deepthoughtnet.clinic.api.medicationsafety;

public enum MedicationSafetyReviewDecisionStatus {
    NOT_REVIEWED,
    REVIEWED_NO_BLOCKING_FINDINGS,
    WARNINGS_ACKNOWLEDGED,
    CRITICAL_OVERRIDE_APPROVED,
    STALE,
    INVALIDATED,
    FINALIZED
}
