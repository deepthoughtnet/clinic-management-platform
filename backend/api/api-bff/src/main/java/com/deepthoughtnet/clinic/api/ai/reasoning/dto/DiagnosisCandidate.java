package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;
import java.util.List;

public record DiagnosisCandidate(
        String name,
        BigDecimal confidence,
        String status,
        String whyConsidered,
        String whyLessLikely,
        List<EvidenceItem> supportingEvidence,
        List<EvidenceItem> contradictingEvidence,
        List<MissingInformationItem> missingInformation,
        List<RecommendedTestItem> recommendedTests,
        List<RedFlagItem> redFlags
) {
}
