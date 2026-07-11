package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalReasoningResult(
        UUID consultationId,
        UUID patientId,
        OffsetDateTime generatedAt,
        String provider,
        String model,
        String confidence,
        ClinicalReasoningLongitudinalContext longitudinalContext,
        DiagnosisCandidate primaryDiagnosis,
        List<DiagnosisCandidate> differentialDiagnoses,
        List<EvidenceItem> supportingEvidence,
        List<EvidenceItem> contradictingEvidence,
        List<MissingInformationItem> missingInformation,
        List<RedFlagItem> redFlags,
        List<RecommendedTestItem> recommendedTests,
        String reasoningSummary,
        List<ClinicalSafetyNote> safetyNotes,
        List<String> followUpAdvice,
        String patientExplanation,
        SourceContextSummary sourceContextSummary,
        ReasoningMetadata metadata
) {
    public record SourceContextSummary(
            String chiefComplaint,
            List<String> symptoms,
            String vitals,
            List<String> knownConditions,
            List<String> recentReports,
            List<String> currentMedicines
    ) {}
}
