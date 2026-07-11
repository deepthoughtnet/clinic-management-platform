package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.util.List;

public record ClinicalReasoningLongitudinalContext(
        List<ClinicalReasoningFinding> findings
) {
}
