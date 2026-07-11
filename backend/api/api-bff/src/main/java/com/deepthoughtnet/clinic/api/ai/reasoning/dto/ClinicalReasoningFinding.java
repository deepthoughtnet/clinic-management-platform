package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;

public record ClinicalReasoningFinding(
        String title,
        String summary,
        String clinicalRelevance,
        String sourceDate,
        String sourceType,
        String sourceReference,
        String verificationStatus,
        String importance,
        BigDecimal confidence
) {
}
