package com.deepthoughtnet.clinic.api.medicationsafety;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MedicationSafetyEvaluationResult(
        String evaluationId,
        OffsetDateTime evaluatedAt,
        UUID prescriptionId,
        MedicationSafetySeverity overallSeverity,
        List<MedicationSafetyFinding> findings,
        List<String> dataQualityWarnings,
        MedicationSafetyCoverage evaluationCoverage,
        String rulesVersion,
        SourceSnapshotMetadata sourceSnapshotMetadata
) {
    public record SourceSnapshotMetadata(
            UUID tenantId,
            UUID patientId,
            UUID consultationId,
            UUID prescriptionId,
            String prescriptionStatus
    ) {
    }
}
