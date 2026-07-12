package com.deepthoughtnet.clinic.api.medicationsafety;

import java.util.List;

public record MedicationSafetyReviewRequest(
        String evaluationId,
        String prescriptionHash,
        String patientContextHash,
        String rulesVersion,
        List<MedicationSafetyFindingReviewDecision> findings
) {
}
