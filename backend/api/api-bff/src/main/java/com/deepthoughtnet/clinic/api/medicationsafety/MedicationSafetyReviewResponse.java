package com.deepthoughtnet.clinic.api.medicationsafety;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MedicationSafetyReviewResponse(
        UUID reviewId,
        UUID consultationId,
        UUID prescriptionId,
        UUID patientId,
        String evaluationId,
        String prescriptionHash,
        String patientContextHash,
        String rulesVersion,
        String decisionStatus,
        boolean stale,
        boolean readyForFinalization,
        String requiredAction,
        OffsetDateTime reviewedAt,
        UUID reviewedByAppUserId,
        String evaluationOverallSeverity,
        int actionableFindingCount,
        int warningFindingCount,
        int criticalFindingCount,
        List<MedicationSafetyFindingReviewStatus> findingReviews,
        List<String> dataQualityWarnings
) {
}
